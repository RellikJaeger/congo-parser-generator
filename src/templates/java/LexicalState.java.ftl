/* Generated by: ${generated_by}. Do not edit. 
 * Generated Code for LexicalState
 * by the LexicalState.java.ftl template
 */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]

public enum LexicalState {
  [#list grammar.lexerData.lexicalStates as lexicalState]
     ${lexicalState.name}
     [#if lexicalState_has_next],[/#if]
  [/#list]
}

