package com.gathin.tvhome;

/** Breaks long paired verses at punctuation; keeps short phrases such as 鹅，鹅，鹅 together. */
final class PoetryText {
    static String listening(String body) {
        StringBuilder result=new StringBuilder(),line=new StringBuilder();
        for(String clause:body.replace("\n","").split("(?<=[，。！？；])")){
            if(line.length()>0&&line.length()+clause.length()>9){append(result,line);line.setLength(0);}
            line.append(clause);
            if(clause.endsWith("。")||clause.endsWith("！")||clause.endsWith("？")){append(result,line);line.setLength(0);}
        }
        if(line.length()>0)append(result,line);return result.toString();
    }
    private static void append(StringBuilder result,StringBuilder line){if(result.length()>0)result.append('\n');result.append(line);}
}
