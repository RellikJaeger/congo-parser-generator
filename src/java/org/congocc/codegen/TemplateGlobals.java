package org.congocc.codegen;

import java.util.*;

import org.congocc.app.*;
import org.congocc.core.*;
import org.congocc.parser.*;
import org.congocc.parser.tree.*;

/**
 * Class to hold various methods and variables
 * that are exposed to the template layer
 */
public class TemplateGlobals {

    private Grammar grammar;
    private LexerData lexerData;
    private AppSettings appSettings;
    private Translator translator;

    private List<String> nodeVariableNameStack = new ArrayList<>();

    public TemplateGlobals(Grammar grammar) {
        this.grammar = grammar;
        this.lexerData = grammar.getLexerData();
        this.appSettings = grammar.getAppSettings();
    }

    public void setTranslator(Translator translator) {this.translator = translator;}

    public void pushNodeVariableName(String nodeName) {
        nodeVariableNameStack.add(nodeName);
    }

    public void popNodeVariableName() {
        nodeVariableNameStack.remove(nodeVariableNameStack.size() - 1);
    }

    public boolean nodeIsInterface(String nodeName) {
        return grammar.nodeIsInterface(nodeName);
    }

    public String addEscapes(String str) {
        // TODO delegate to code in Lexer
        StringBuilder retval = new StringBuilder();
        for (int ch : str.codePoints().toArray()) {
            switch (ch) {
                case '\b':
                    retval.append("\\b");
                    continue;
                case '\t':
                    retval.append("\\t");
                    continue;
                case '\n':
                    retval.append("\\n");
                    continue;
                case '\f':
                    retval.append("\\f");
                    continue;
                case '\r':
                    retval.append("\\r");
                    continue;
                case '\"':
                    retval.append("\\\"");
                    continue;
                case '\'':
                    retval.append("\\\'");
                    continue;
                case '\\':
                    retval.append("\\\\");
                    continue;
                default:
                    if (Character.isISOControl(ch)) {
                        String s = "0000" + java.lang.Integer.toString(ch, 16);
                        retval.append("\\u" + s.substring(s.length() - 4, s.length()));
                    } else {
                        retval.appendCodePoint(ch);
                    }
                    continue;
            }
        }
        return retval.toString();
    }

    // For use from templates.
    public String getPreprocessorSymbol(String key, String defaultValue) {
        return grammar.getPreprocessorSymbols().getOrDefault(key, defaultValue);
    }

    /**
     * @param ch the code point. If it is not ASCII, we just display the integer in
     *           hex.
     * @return a String to use in generated Java code. Rather than display the
     *         integer 97, we display 'a',
     *         for example.
     */

    public String displayChar(int ch) {
        String s;

        if (ch == '\'')
            return "\'\\'\'";
        if (ch == '\\')
            return "\'\\\\\'";
        if (ch == '\t')
            return "\'\\t\'";
        if (ch == '\r')
            return "\'\\r\'";
        if (ch == '\n')
            return "\'\\n\'";
        if (ch == '\f')
            return "\'\\f\'";
        if (ch == ' ')
            return "\' \'";
        if (ch < 128 && !Character.isWhitespace(ch) && !Character.isISOControl(ch))
            return "'" + (char) ch + "'";
        s = "0x" + Integer.toHexString(ch);
        if (appSettings.getCodeLang().equals("python")) {
            s = String.format("as_chr(%s)", s);
        }
        return s;
    }

    /**
     * This method is only here to help with debugging NFA state-related logic in
     * templates.
     * Sometimes, you want to see ASCII rather than code points.
     *
     * @param char_array a list of code points.
     * @return a String to use in generated template code.
     */
    public String displayChars(int[] char_array) {
        StringBuilder sb = new StringBuilder();
        int n = char_array.length;

        sb.append('[');
        for (int i = 0; i < n; i++) {
            sb.append(displayChar(char_array[i]));
            if (i < (n - 1)) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb.toString();
    }

    // The following methods added for supporting generation in languages other than
    // Java. (It is only called from non-Java-generating templates, i.e. .cs.ftl and .py.ftl)
    public Map<String, Object> tokenSubClassInfo() {
        Map<String, String> tokenClassMap = new HashMap<>();
        Map<String, String> superClassMap = new HashMap<>();
        // List<String> classes = new ArrayList<>();

        for (RegularExpression re : lexerData.getOrderedNamedTokens()) {
            if (re.isPrivate())
                continue;
            String tokenClassName = re.getGeneratedClassName();
            String superClassName = re.getGeneratedSuperClassName();

            if (superClassName == null) {
                superClassName = "Token";
            } else {
                if (!superClassMap.containsKey(superClassName)) {
                    // classes.add(superClassName);
                    superClassMap.put(superClassName, null); // TODO not always!
                }
            }
            if (!tokenClassMap.containsKey(tokenClassName)) {
                // classes.add(tokenClassName);
                tokenClassMap.put(tokenClassName, superClassName);
            }
        }
        // Sort out superclasses' superclasses
        String pkg = appSettings.getNodePackage();
        for (String key : superClassMap.keySet()) {
            String qualifiedName = String.format("%s.%s", pkg, key);
            List<ObjectType> extendsList = grammar.getInjector().getExtendsList(qualifiedName);

            if ((extendsList == null) || (extendsList.size() == 0)) {
                superClassMap.put(key, "Token");
            } else {
                superClassMap.put(key, extendsList.get(0).toString());
            }
        }
        tokenClassMap.putAll(superClassMap);

        // Topologically sort classes
        Sequencer seq = new Sequencer();
        for (Map.Entry<String, String> entry : tokenClassMap.entrySet()) {
            seq.addNode(entry.getKey());
            seq.addNode(entry.getValue());
            seq.add(entry.getKey(), entry.getValue());
        }
        List<String> sorted = seq.steps("Token");
        sorted.remove(0);
        Map<String, Object> result = new HashMap<>();
        result.put("sortedNames", sorted);
        result.put("tokenClassMap", tokenClassMap);
        return result;
    }

    // Used in templates specifically for method name translation
    public String translateIdentifier(String ident) {
        return translator.translateIdentifier(ident, Translator.TranslationContext.METHOD);
    }

    // Used in templates for side effects, hence returning empty string
    public String startProduction() {
        Translator.SymbolTable symbols = new Translator.SymbolTable();
        translator.pushSymbols(symbols);
        return "";
    }

    // Used in templates for side effects, hence returning empty string
    public String endProduction() {
        translator.popSymbols();
        translator.clearParameterNames();
        return "";
    }

    public String translateParameters(String parameterList) {
        StringBuilder sb = new StringBuilder();
        // First construct the parameter list with parentheses, so
        // that we can parse it and get the AST
        sb.append('(');
        sb.append(parameterList);
        sb.append(')');
        CongoCCParser parser = new CongoCCParser(sb);
        parser.FormalParameters();
        List<FormalParameter> parameters = ((FormalParameters) parser.rootNode()).getParams();
        // Now build the result
        sb.setLength(0);
        translator.translateFormals(parameters, null, sb);
        return sb.toString();
    }

    public String translateExpression(Node expr) {
        StringBuilder result = new StringBuilder();
        translator.translateExpression(expr, result);
        return result.toString();
    }

    public String translateString(String expr) {
        // For debugging. Just parse the passed string as an expression
        // and output the translation.
        CongoCCParser parser = new CongoCCParser(expr);
        parser.Expression();
        StringBuilder result = new StringBuilder();
        translator.translateExpression(parser.rootNode(), result);
        return result.toString();
    }

    private void translateStatements(Node node, int indent, StringBuilder result) {
        if (node instanceof Statement) {
            translator.translateStatement(node, indent, result);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                Node child = node.getChild(i);
                if (child instanceof Delimiter) {
                    continue; // could put in more checks here
                }
                translator.translateStatement(child, indent, result);
            }
        }
    }

    public String translateCodeBlock(String cb, int indent) {
        StringBuilder result = new StringBuilder();
        if (cb != null) {
            cb = cb.trim();
            if (cb.length() == 0) {
                translator.translateEmptyBlock(indent, result);
            } else {
                String block = "{" + cb + "}";
                CongoCCParser parser = new CongoCCParser(block);
                parser.Block();
                Node node = parser.rootNode();
                Translator.SymbolTable syms = new Translator.SymbolTable();
                translator.pushSymbols(syms);
                translateStatements(node, indent, result);
                translator.popSymbols();
            }
        }
        return result.toString();
    }

    // used in templates
    public String translateNonterminalArgs(String args) {
        // The args are passed through as a string, but need to be translated according
        // to the language
        // being generated. For the Java template, they don't come through this method -
        // they are passed
        // straight through as a string by the Java template.
        return (args == null) ? "" : translator.translateNonterminalArgs(args);
    }

    // used in templates
    public String translateInjectedClass(String name) {
        String result;

        translator.startClass(name, false, null);
        result = translator.translateInjectedClass(grammar.getInjector(), name);
        translator.endClass(name, false, null);
        return result;
    }

    public String translateInjections(String className, boolean fields, boolean initializers) {
        StringBuilder result = new StringBuilder();
        if (fields) {
            translator.clearFields();
        }
//        String cn = lastPart(className, '.');
        String cn = className.substring(className.lastIndexOf('.')+1);
        translator.startClass(cn, fields, result);
        try {
            List<ClassOrInterfaceBodyDeclaration> declsToProcess = grammar.getInjector().getBodyDeclarations(className);
            if (declsToProcess != null) {
                int fieldIndent = translator.getFieldIndent();
                int methodIndent = translator.getMethodIndent();
                for (ClassOrInterfaceBodyDeclaration decl : declsToProcess) {
                    // If processing fields, we want to process FieldDeclarations or Initializers.
                    // Otherwise, we want to process TypeDeclarations, MethodDeclarations and
                    // ConstructorDeclarations
                    boolean process = (fields == (decl instanceof FieldDeclaration || decl instanceof Initializer));
                    if (process) {
                        if (decl instanceof FieldDeclaration || decl instanceof CodeBlock
                                || decl instanceof Initializer) {
                            if ((decl instanceof Initializer) && !initializers) {
                                continue;
                            }
                            translator.translateStatement(decl, fieldIndent, result);
                        } else if (decl instanceof MethodDeclaration || decl instanceof ConstructorDeclaration ||
                                decl instanceof EnumDeclaration || decl instanceof ClassDeclaration) {
                            translator.translateStatement(decl, methodIndent, result);
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                }
            }
        } finally {
            translator.endClass(cn, fields, result);
        }
        return result.toString();
    }

    protected String translateInitializers(String className) {
        StringBuilder result = new StringBuilder();
        List<ClassOrInterfaceBodyDeclaration> declsToProcess = grammar.getInjector().getBodyDeclarations(className);
        if (declsToProcess != null) {
            int fieldIndent = translator.getFieldIndent();
            for (ClassOrInterfaceBodyDeclaration decl : declsToProcess) {
                if (decl instanceof Initializer) {
                    translator.translateStatement(decl, fieldIndent, result);
                }
            }
        }
        return result.toString();
    }

    public List<String> injectedFieldNames(String className) {
        ArrayList<String> result = new ArrayList<>();
        Map<String, List<ClassOrInterfaceBodyDeclaration>> bodyDeclarations = grammar.getInjector().getBodyDeclarations();
        List<ClassOrInterfaceBodyDeclaration> declsToProcess = bodyDeclarations.get(className);
        if (declsToProcess != null) {
            for (ClassOrInterfaceBodyDeclaration decl : declsToProcess) {
                if ((decl instanceof MethodDeclaration) ||
                        (decl instanceof ConstructorDeclaration) ||
                        (decl instanceof Initializer) ||
                        (decl instanceof EnumDeclaration) ||
                        (decl instanceof ClassDeclaration)) {
                    continue;
                }
                if (decl instanceof FieldDeclaration) {
                    ArrayList<String> names = new ArrayList<>();
                    for (Node child : decl.children()) {
                        if (child instanceof Identifier) {
                            names.add(((Identifier) child).getImage());
                        } else if (child instanceof VariableDeclarator) {
                            Identifier ident = child.firstChildOfType(Identifier.class);
                            if (ident == null) {
                                throw new UnsupportedOperationException();
                            }
                            names.add(ident.getImage());
                        }
                    }
                    if (names.size() == 0) {
                        throw new UnsupportedOperationException();
                    }
                    for (String name : names) {
                        result.add(translator.translateIdentifier(name,
                                Translator.TranslationContext.VARIABLE));
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        }
        return result;
    }

    public List<String> injectedTokenFieldNames() {
        String className = String.format("%s.Token", appSettings.getParserPackage());
        return injectedFieldNames(className);
    }

    public List<String> injectedLexerFieldNames() {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getLexerClassName());
        return injectedFieldNames(className);
    }

    // used in templates
    public List<String> injectedParserFieldNames() {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getParserClassName());
        return injectedFieldNames(className);
    }

    // used in templates
    public String translateNestedTypes(String className, boolean fields) {
        className = String.format("%s.%s", appSettings.getNodePackage(), className);
        return translateInjections(className, fields, false);
    }

    // used in templates
    public String translateTokenInjections(boolean fields) {
        String className = String.format("%s.Token", appSettings.getParserPackage());
        return translateInjections(className, fields, fields && translator.isIncludeInitializers());
    }

    // used in templates
    public String translateLexerInjections(boolean fields) {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getLexerClassName());
        return translateInjections(className, fields, fields && translator.isIncludeInitializers());
    }

    // used in templates
    public String translateParserInjections(boolean fields) {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getParserClassName());
        return translateInjections(className, fields, fields && translator.isIncludeInitializers());
    }

    // used in templates
    public String translateLexerInitializers() {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getLexerClassName());
        return translateInitializers(className);
    }

    // used in templates
    public String translateParserInitializers() {
        String className = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getParserClassName());
        return translateInitializers(className);
    }

    // used in templates
    public String translateTokenSubclassInjections(String className, boolean fields) {
        className = String.format("%s.%s", appSettings.getNodePackage(), className);
        return translateInjections(className, fields, fields && translator.isIncludeInitializers());
    }

    // used in templates
    public String translateType(String type) {
        return translator.translateTypeName(type);
    }

    // used in templates
    public String translateModifiers(String modifiers) {
        return modifiers;
    }

    protected void processImports(Set<ImportDeclaration> imports, StringBuilder result) {
        String prefix = String.format("%s.", appSettings.getNodePackage());
        for (ImportDeclaration decl : imports) {
            String name = decl.getChild(1).toString();
            if (name.startsWith("java.") || name.startsWith(prefix)) {
                continue;
            }
            translator.translateImport(name, result);
        }
    }

    // used in templates
    public String translateLexerImports() {
        StringBuilder result = new StringBuilder();
        String cn = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getLexerClassName());
        Set<ImportDeclaration> imports = grammar.getInjector().getImportDeclarations(cn);

        if (imports != null) {
            processImports(imports, result);
        }
        return result.toString();
    }

    // used in templates
    public String translateParserImports() {
        StringBuilder result = new StringBuilder();
        String cn = String.format("%s.%s", appSettings.getParserPackage(), appSettings.getParserClassName());
        Set<ImportDeclaration> imports = grammar.getInjector().getImportDeclarations(cn);

        if (imports != null) {
            processImports(imports, result);
        }
        return result.toString();
    }

    public List<String> getSortedNodeClassNames() {
        Sequencer seq = new Sequencer();
        String pkg = appSettings.getNodePackage();
        String bnn = appSettings.getBaseNodeClassName();

        seq.addNode(bnn);
        for (String cn : grammar.getNodeNames()) {
            String qn = String.format("%s.%s", pkg, cn);
            List<ObjectType> elist = grammar.getInjector().getExtendsList(qn);
            List<ObjectType> ilist = grammar.getInjector().getImplementsList(qn);
            List<String> preds = new ArrayList<>();
            if (elist != null) {
                for (ObjectType ot : elist) {
                    preds.add(ot.toString());
                }
            }
            if (ilist != null) {
                for (ObjectType ot : ilist) {
                    preds.add(ot.toString());
                }
            }
            if (preds.isEmpty()) {
                preds.add(bnn);
            }
            for (String pn : preds) {
                seq.addNode(pn);
                seq.addNode(cn);
                seq.add(cn, pn); // Add in reverse order
            }
        }
        List<String> result = seq.steps(bnn);
        result.remove(0); // The bnn value
        return result;
    }

    public String getCurrentNodeVariableName() {
        if (nodeVariableNameStack.isEmpty())
            return "null";
        return nodeVariableNameStack.get(nodeVariableNameStack.size() - 1);
    }
}