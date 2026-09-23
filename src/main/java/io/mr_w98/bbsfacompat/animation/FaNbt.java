package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.jem.CemParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class FaNbt {
    private static final Pattern CALL = Pattern.compile("(?<![\\w.])nbt\\s*\\(");
    private final List<Query> queries = new ArrayList<>();

    public String rewrite(String expression) {
        var matcher = CALL.matcher(expression);
        StringBuilder result = new StringBuilder();
        int from = 0;
        while (matcher.find(from)) {
            int depth = 1;
            int end = matcher.end();
            for (; end < expression.length() && depth != 0; end++) {
                if (expression.charAt(end) == '(') depth++;
                if (expression.charAt(end) == ')') depth--;
            }
            if (depth != 0) throw new IllegalArgumentException("Unclosed NBT expression: " + expression);
            String call = expression.substring(matcher.end(), end - 1);
            int comma = call.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("Missing NBT matcher: " + call);

            Query query = new Query(call.substring(0, comma).trim(), call.substring(comma + 1).trim());
            int index = queries.indexOf(query);
            if (index < 0) {
                index = queries.size();
                queries.add(query);
            }
            result.append(expression, from, matcher.start()).append("fa_nbt_").append(index);
            from = end;
        }
        return result.append(expression, from, expression.length()).toString();
    }

    public void update(CemParser parser, Function<String, String> values) {
        for (int i = 0; i < queries.size(); i++) {
            Query query = queries.get(i);
            parser.setValue("fa_nbt_" + i, query.matches(values.apply(query.path)) ? 1 : 0);
        }
    }

    private static final class Query {
        private final String path;
        private final String matcher;
        private final Pattern regex;

        private Query(String path, String matcher) {
            this.path = path;
            this.matcher = matcher;
            String pattern = matcher.startsWith("raw:") ? matcher.substring(4) : matcher;
            this.regex = pattern.startsWith("iregex:") ? Pattern.compile(pattern.substring(7), Pattern.CASE_INSENSITIVE)
                : pattern.startsWith("regex:") ? Pattern.compile(pattern.substring(6)) : null;
            if (!List.of("SleepingX", "abilities.flying", "SelectedItem.id", "Inventory", "equipment.offhand").contains(path)) {
                throw new IllegalArgumentException("Unsupported FA Player NBT path: " + path);
            }
        }

        private boolean matches(String value) {
            if (matcher.equals("exists:true")) return value != null;
            if (matcher.equals("exists:false")) return value == null;
            if (value == null) return false;
            return regex == null ? matcher.equals(value) : regex.matcher(value).matches();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Query query && path.equals(query.path) && matcher.equals(query.matcher);
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + matcher.hashCode();
        }
    }
}
