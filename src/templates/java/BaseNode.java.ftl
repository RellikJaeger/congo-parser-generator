[#ftl strict_vars=true]
/* Generated by: ${generated_by}. Do not edit. ${filename} ${grammar.copyrightBlurb} */
[#if explicitPackageName?has_content]
package ${explicitPackageName};
[#elseif grammar.nodePackage?has_content]
   [#if grammar.baseNodeInParserPackage]
package ${grammar.parserPackage};
   [#else]
package ${grammar.nodePackage};
   [/#if]
[/#if]
[#if !grammar.baseNodeInParserPackage && grammar.parserPackage?has_content && grammar.nodePackage != grammar.parserPackage]
import ${grammar.parserPackage}.*;
[/#if]
[#if grammar.settings.FREEMARKER_NODES?? && grammar.settings.FREEMARKER_NODES]
import freemarker.template.*;
[/#if]

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

/**
 * The base concrete class for non-terminal Nodes
 */
public class ${grammar.baseNodeClassName} implements Node {
    private TokenSource tokenSource;
    
    public TokenSource getTokenSource() {
        if (tokenSource==null) {
            for (Node child : children()) {
                tokenSource = child.getTokenSource();
                if (tokenSource != null) break;
            }
        }
        return tokenSource;
    }

    public void setTokenSource(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }
    
    static private Class<? extends List> listClass;

    /**
     * Sets the List class that is used to store child nodes. By default,
     * this is java.util.ArrayList. There is probably very little reason
     * to ever use anything else, though you could use this method 
     * to replace this with LinkedList or your own java.util.List implementation even.
     * @param listClass the #java.util.List implementation to use internally 
     * for the child nodes. By default #java.util.ArrayList is used.
     */
	static public void setListClass(Class<? extends List> listClass) {
        ${grammar.baseNodeClassName}.listClass = listClass;
    }

    @SuppressWarnings("unchecked")
    private List<Node> newList() {
        if (listClass == null) {
            return new ArrayList<>();
        }
        try {
           return (List<Node>) listClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
           throw new RuntimeException(e);
        }
    }
    /**
     * the parent node
     */    
    private Node parent;

    /**
     * the child nodes
     */
    private List<Node> children = newList();
    
    private int beginOffset, endOffset;
    private boolean unparsed;
    
    public boolean isUnparsed() {
       return this.unparsed;
    }
    
    public void setUnparsed(boolean unparsed) {
        this.unparsed = unparsed;
    }

[#if grammar.faultTolerant]

    private boolean dirty;

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

[/#if]

[#if grammar.nodeUsesParser]    
    protected ${grammar.parserClassName} parser;

    public void setParser(${grammar.parserClassName} parser) {this.parser = parser;}
[/#if]

    public void setParent(Node n) {
        parent = n;
    }

    public Node getParent() {
        return parent;
    }

    public void addChild(Node n) {
        children.add(n);
        n.setParent(this);
    }
    
    public void addChild(int i, Node n) {
        children.add(i, n);
        n.setParent(this);
    }

    public Node getChild(int i) {
        return children.get(i);
    }

    public void setChild(int i, Node n) {
        children.set(i, n);
        n.setParent(this);
    }
    
    public Node removeChild(int i) {
        return children.remove(i);
    }

    public void clearChildren() {
        children.clear();
    }

    public int getChildCount() {
        return children.size();
    }
    
    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }
    
    public int getBeginOffset() {
        return beginOffset;
    }

    public void setBeginOffset(int beginOffset) {
        this.beginOffset = beginOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }


     
[#if grammar.settings.FREEMARKER_NODES?? && grammar.settings.FREEMARKER_NODES]
    public TemplateSequenceModel getChildNodes() {
        SimpleSequence seq = new SimpleSequence();
        for (Node child : children) {
            seq.add(child);
        }
        return seq;
    }
    
    public TemplateNodeModel getParentNode() {
        return this.parent;
    }
    
    public String getNodeName() {
         return this.getClass().getSimpleName();
    }
    
    public String getNodeType() {
        return "";
    }
    
    public String getNodeNamespace() {
        return null;
    }
    
    public String getAsString() throws TemplateModelException {
        StringBuilder buf = new StringBuilder();
        if (children != null) {
	        for (Node child : children) {
	            buf.append(child.getAsString());
	            buf.append(" ");
	        }
	    }
        return buf.toString();
    }
[/#if]    
    
    public String toString() {
        StringBuilder buf=new StringBuilder();
        for(Token t : getRealTokens()) {
            buf.append(t);
        }
        return buf.toString();
    }

    private Map<String, Node> namedChildMap;
    private Map<String, List<Node>> namedChildListMap;

    public Node getNamedChild(String name) {
        if (namedChildMap == null) {
            return null;
        }
        return namedChildMap.get(name);
    }

    public void setNamedChild(String name, Node node) {
        if (namedChildMap == null) {
            namedChildMap = new HashMap<>();
        }
        if (namedChildMap.containsKey(name)) {
            // Can't have duplicates
            String msg = String.format("Duplicate named child not allowed: {0}", name);
            throw new RuntimeException(msg);
        }
        namedChildMap.put(name, node);
    }

    public List<Node> getNamedChildList(String name) {
        if (namedChildListMap == null) {
            return null;
        }
        return namedChildListMap.get(name);
    }

    public void addToNamedChildList(String name, Node node) {
        if (namedChildListMap == null) {
            namedChildListMap = new HashMap<>();
        }
        List<Node> nodeList = namedChildListMap.get(name);
        if (nodeList == null) {
            nodeList = new ArrayList<>();
            namedChildListMap.put(name, nodeList);
        }
        nodeList.add(node);
    }
}
