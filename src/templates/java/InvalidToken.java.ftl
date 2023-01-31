/* Generated by: ${generated_by}. ${filename} ${grammar.copyrightBlurb} */
[#if grammar.parserPackage?has_content]
package ${grammar.parserPackage};
[/#if]

/**
 * Token subclass to represent lexically invalid input
 */
public class InvalidToken extends Token 
[#if grammar.faultTolerant] implements ParsingProblem [/#if] {

    public InvalidToken(TokenSource tokenSource, int beginOffset, int endOffset) {
        super(TokenType.INVALID, tokenSource, beginOffset, endOffset);
[#if grammar.faultTolerant]
        super.setUnparsed(true);
        this.setDirty(true);
[/#if]
    }

    public String getNormalizedText() {
        return "Lexically Invalid Input:" + getImage();
    }

 [#if grammar.faultTolerant]
    
    private ParseException cause;
    private String errorMessage;

    void setCause(ParseException cause) {
        this.cause = cause;
    }

    public ParseException getCause() {
        return cause;
    }

    public String getErrorMessage() {
        if (errorMessage != null) return errorMessage;
        return "lexically invalid input"; // REVISIT
    }
 [/#if]

}
