package org.congocc.core;

import org.congocc.parser.Token.TokenType;
import org.congocc.parser.tree.*;

public class NonTerminal extends Expansion {
    
    private Name LHS;
    public Name getLHS() {return LHS;}
    public void setLHS(Name LHS) {this.LHS=LHS;}

    /**
     * The production this non-terminal corresponds to.
     */
    public BNFProduction getProduction() {
        return getGrammar().getProductionByName(getName());
    }

    public Expansion getNestedExpansion() {
        return getProduction().getExpansion();
    }

    public Lookahead getLookahead() {
        return getNestedExpansion().getLookahead();
    }

    public InvocationArguments getArgs() {
        return firstChildOfType(InvocationArguments.class);
    }

    public String getName() {
        return firstChildOfType(TokenType.IDENTIFIER).getSource();
    }
    
    /**
     * The basic logic of when we actually stop at a scan limit 
     * encountered inside a NonTerminal
     */

    public boolean getStopAtScanLimit() {
        if (isInsideLookahead()) return false;
        ExpansionSequence parent = (ExpansionSequence) getNonSuperfluousParent();
        if (!parent.isAtChoicePoint()) return false;
        if (parent.getHasExplicitNumericalLookahead() || parent.getHasExplicitScanLimit()) return false;
        return parent.firstNonEmpty() == this;
    }

    public final boolean getScanToEnd() {
        return !getStopAtScanLimit();
    }

    public TokenSet getFirstSet() {
        if (firstSet == null) {
            firstSet = getProduction().getExpansion().getFirstSet();
        }
        return firstSet;
     }
     private int reEntries;     
     public TokenSet getFinalSet() {
          ++reEntries;
          TokenSet result = reEntries == 1 ? getProduction().getExpansion().getFinalSet() : new TokenSet(getGrammar());
          --reEntries;
          return result;
     }
     
     public boolean isPossiblyEmpty() {
         return getProduction().isPossiblyEmpty();
     }

     @Override
     public boolean isAlwaysEntered() {
         return getProduction().getExpansion().isAlwaysEntered();
     }
     
     private boolean inMinimumSize, inMaximumSize;
     
     public int getMinimumSize() {
         if (inMinimumSize) return Integer.MAX_VALUE;
         inMinimumSize = true;
         int result = getProduction().getExpansion().getMinimumSize();
         inMinimumSize = false;
         return result;
     }

     public int getMaximumSize() {
         if (inMaximumSize) {
             return Integer.MAX_VALUE;
         }
         inMaximumSize = true;
         int result = getProduction().getExpansion().getMaximumSize(); 
         inMaximumSize = false;
         return result;
     }
    
     
     // We don't nest into NonTerminals
     @Override
     public boolean getHasScanLimit() {
        Expansion exp = getNestedExpansion();
        if (exp instanceof ExpansionSequence) {
            for (Expansion sub : ((ExpansionSequence) exp).allUnits()) {
                if (sub.isScanLimit()) return true;
            }
        }
        return false;
     }

     public boolean isSingleToken() {
        return getNestedExpansion().isSingleToken();
     }

     public boolean startsWithLexicalChange() {
        if (getProduction().getLexicalState() != null) return true;
        Expansion nested = getNestedExpansion();
        if (nested instanceof ExpansionSequence) {
            for (Expansion sub : nested.childrenOfType(Expansion.class)) {
                if (!(sub instanceof NonTerminal)) {
                    // KLUDGE? For now we don't nest further into nonterminals
                    // It seems like we should, but the code blows up. I need
                    // to revisit this! For now, it's "good enough for government work, I suppose."
                    if (sub.startsWithLexicalChange()) return true;
                }
                if (!sub.isPossiblyEmpty()) break;
            }
        }
        return false;
     }

     public boolean startsWithGlobalCodeAction() {
        CodeBlock javaCode = getProduction().getJavaCode();
        if (javaCode != null && javaCode.isAppliesInLookahead()) return true;
        Expansion nested = getNestedExpansion();
        if (nested instanceof ExpansionSequence) {
            for (Expansion sub: nested.childrenOfType(Expansion.class)) {
                if (!(sub instanceof NonTerminal)) {
                    // We don't nest recursively into nonterminals here either.
                    if (sub.startsWithLexicalChange()) return true;
                }
                if (!sub.isPossiblyEmpty()) break;
            }
        }
        return false;
     }
}