package io.mr_w98.bbsfacompat.animation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class FaExpression {
    private final String source;
    private int cursor;
    private String token;

    private FaExpression(String source) {
        this.source = source;
        next();
    }

    public static String normalize(String source) {
        FaExpression parser = new FaExpression(source);
        String result = parser.expression(1);
        if (!parser.token.isEmpty()) throw parser.error("Unexpected token " + parser.token);
        return result;
    }

    private String expression(int minimum) {
        String left = primary();
        while (precedence(token) >= minimum) {
            String operator = token;
            int priority = precedence(operator);
            next();
            String right = expression(priority + 1);
            left = "(" + left + operator + right + ")";
        }
        return left;
    }

    private String primary() {
        String current = token;
        if (current.equals("!") || current.equals("-") || current.equals("+")) {
            next();
            String value = primary();
            return current.equals("+") ? value : current + "(" + value + ")";
        }
        if (current.equals("(")) {
            next();
            String value = expression(1);
            consume(")");
            return "(" + value + ")";
        }
        if (current.isEmpty() || precedence(current) > 0 || current.equals(",") || current.equals(")")) {
            throw error("Expected value, found " + current);
        }
        next();
        if (!token.equals("(")) return current;

        next();
        List<String> arguments = new ArrayList<>();
        if (!token.equals(")")) {
            arguments.add(expression(1));
            while (token.equals(",")) {
                next();
                arguments.add(expression(1));
            }
        }
        consume(")");
        return current + "(" + String.join(",", arguments) + ")";
    }

    private static int precedence(String operator) {
        return switch (operator) {
            case "||" -> 1;
            case "&&" -> 2;
            case "==", "!=" -> 3;
            case "<", "<=", ">", ">=" -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            case "^" -> 7;
            default -> 0;
        };
    }

    private void consume(String expected) {
        if (!token.equals(expected)) throw error("Expected " + expected + ", found " + token);
        next();
    }

    private void next() {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        if (cursor == source.length()) {
            token = "";
            return;
        }
        int start = cursor;
        char first = source.charAt(cursor++);
        if ("()+-*/%^!,<>=&|".indexOf(first) >= 0) {
            if (cursor < source.length() && List.of("&&", "||", "==", "!=", "<=", ">=").contains(source.substring(start, cursor + 1))) cursor++;
        } else if (Character.isDigit(first) || first == '.') {
            while (cursor < source.length() && (Character.isDigit(source.charAt(cursor)) || source.charAt(cursor) == '.')) cursor++;
            if (cursor < source.length() && (source.charAt(cursor) == 'e' || source.charAt(cursor) == 'E')) {
                cursor++;
                if (cursor < source.length() && (source.charAt(cursor) == '+' || source.charAt(cursor) == '-')) cursor++;
                while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor++;
            }
            token = new BigDecimal(source.substring(start, cursor)).toPlainString();
            return;
        } else {
            if (!Character.isLetter(first) && first != '_') throw error("Invalid character " + first);
            while (cursor < source.length()) {
                char c = source.charAt(cursor);
                if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != ':') break;
                cursor++;
            }
        }
        token = source.substring(start, cursor);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + cursor + " in " + source);
    }
}
