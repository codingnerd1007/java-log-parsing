/*
 * Copyright (c) Motadata 2024. All rights reserved.
 */


import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class LogPatternCacheStore
{
    private static final LogPatternCacheStore STORE = new LogPatternCacheStore();
    private static final String COMMA_SEPARATOR = ",";
    private static final double MATCHING_SCORE = 0.5;
    private static final String PATTERN_PLACE_HOLDER = "****";
    private static final Map<Pattern, String> MASKING_PATTERNS = new LinkedHashMap<>();
    private final Map<String, Integer> items = new ConcurrentHashMap<>();//it will have pattern as key and value will be patternId
    private final Map<String, String> patternIds = new ConcurrentHashMap<>();//pluginid and comma separated patternIds
    private final Map<Integer, String> patterns = new ConcurrentHashMap<>(); // pattern id with pattern
    private final AtomicInteger patternId = new AtomicInteger();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private LogPatternCacheStore()
    {
    }
    public static LogPatternCacheStore getStore()
    {
        return STORE;
    }
    public Map<String, Integer> getItems()
    {
        return this.items;
    }
    public Map<String, String> getPatternIds()
    {
        return this.patternIds;
    }
    public Map<Integer, String> getPatterns()
    {
        return  this.patterns;
    }

    public void initStore()
    {
        loadPatterns();
        System.out.printf("store %s initialized...%n", this.getClass().getSimpleName());
    }
    public int detectPattern(String plugin, String message)
    {
        try
        {
            message = mask(message);
            var messageTokens = message.split(" ");
            if (patternIds.containsKey(plugin))
            {
                var ids = Arrays.stream(patternIds.get(plugin).split(COMMA_SEPARATOR)).toList();
                var builder = new StringBuilder();
                for (var id : ids)
                {
                    builder.setLength(0);
                    var pattern = patterns.get(Integer.parseInt(id));
                    var patternTokens = pattern.split(" ");
                    var min = 0;
                    var max = 0;
                    if (patternTokens.length > messageTokens.length)
                    {
                        min = messageTokens.length;
                        max = patternTokens.length;
                    }
                    else
                    {
                        min = patternTokens.length;
                        max = messageTokens.length;
                    }
                    var tokens = matchTokens(patternTokens, messageTokens, min, max);
                    if (tokens != null && !tokens.isEmpty())
                    {
                        for (var index = 0; index < max; index++)
                        {
                            if (tokens.contains(index) && index < patternTokens.length)
                            {
                                builder.append(patternTokens[index]);
                                builder.append(" ");
                            }
                            else
                            {
                                builder.append(PATTERN_PLACE_HOLDER);
                                builder.append(" ");
                            }
                        }
                        var messagePattern = String.valueOf(builder);
                        if (!items.containsKey(messagePattern))
                        {
                            updatePattern(messagePattern, items.remove(pattern), plugin, ids);
                        }
                        return items.get(messagePattern);
                    }
                }
            }
            updatePattern(message, patternId.incrementAndGet(), plugin, null);
        }
        catch (Exception exception)
        {
            System.out.println(Arrays.toString(exception.getStackTrace()));
        }
        return items.getOrDefault(message, 0);
    }
    private void updatePattern(String pattern, int patternId, String plugin, List<String> ids)
    {
        items.put(pattern, patternId);
        patterns.put(patternId, pattern);
        Set<String> patternIds;
        if (ids != null && !ids.isEmpty())
        {
            patternIds = new HashSet<>(ids);
            patternIds.add(String.valueOf(patternId));
            this.patternIds.put(plugin, StringUtils.join(patternIds, COMMA_SEPARATOR));
        }
        else
        {
            if (this.patternIds.containsKey(plugin))
            {
                this.patternIds.put(plugin, this.patternIds.get(plugin) + COMMA_SEPARATOR + patternId);
            }
            else
            {
                patternIds = new HashSet<>();
                patternIds.add(String.valueOf(patternId));
                this.patternIds.put(plugin, StringUtils.join(patternIds, COMMA_SEPARATOR));
            }
        }
        dirty.set(true);
    }
    private List<Integer> matchTokens(String[] patternTokens, String[] messageTokens, int min, int max)
    {
        var patternScore = 0f;
        var tokens = new ArrayList<Integer>();
        for (var index = 0; index < min; index++)
        {
            var score = getScore(patternTokens[index].trim(), messageTokens[index].trim());
            patternScore += 1 * (score / max);
            if (score == 1)
            {
                tokens.add(index);
            }
        }
        if ((1 - patternScore) <= MATCHING_SCORE)
        {
            return tokens;
        }
        return null;
    }
    private float getScore(String token1, String token2)
    {
        if (!token1.isEmpty() && !token2.isEmpty() && !token1.equals(PATTERN_PLACE_HOLDER) && !token2.equals(PATTERN_PLACE_HOLDER) && token1.equals(token2))
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }
    public boolean dirty()
    {
        return dirty.get();
    }
    public void setDirty(boolean value)
    {
        dirty.set(value);
    }

    public String getPattern(int patternId)
    {
        return patterns.getOrDefault(patternId, null);
    }
    public void loadPatterns()
    {
        try
        {
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)(([0-9a-f]{2,}:){3,}([0-9a-f]{2,}))((?=[^A-Za-z0-9])|$)"), "<ID>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)(((https|http):\\/\\/|www[.])[A-Za-z0-9+&@#\\/%?=~_()-|!:,.;]*[-A-Za-z0-9+&@#\\/%=~_()|])((?=[^A-Za-z0-9])|$)"), "<URL>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})((?=[^A-Za-z0-9])|$)"), "<IP>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)([\\w]{8}\\b-[\\w]{4}\\b-[\\w]{4}\\b-[\\w]{4}\\b-[\\w]{12})((?=[^A-Za-z0-9])|$)"), "<GUID>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)([\\w+\\.+\\-]+@+[\\w+\\.+\\-]+[\\.\\w]{2,})((?=[^A-Za-z0-9])|$)"), "<EMAIL>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)([\\-\\+]?\\d+)((?=[^A-Za-z0-9])|$)"), "<NUM>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)([0-9a-f]{6,} ?){3,}((?=[^A-Za-z0-9])|$)"), "<SEQ>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)([0-9A-F]{4} ?){4,}((?=[^A-Za-z0-9])|$)"), "<SEQ>");
            MASKING_PATTERNS.put(Pattern.compile("((?<=[^A-Za-z0-9])|^)(0x[a-f0-9A-F]+)((?=[^A-Za-z0-9])|$)"), "<HEX>");
            MASKING_PATTERNS.put(Pattern.compile("(?<=executed cmd )(\".+?\")"), "<CMD>");
        }
        catch (Exception exception)
        {
            System.out.println(Arrays.toString(exception.getStackTrace()));
        }
    }
//    public void dump(String path)
//    {
//        if (!items.isEmpty())
//        {
//            Bootstrap.vertx().<Void>executeBlocking(promise ->
//            {
//                try
//                {
//                    Bootstrap.vertx().fileSystem().writeFileBlocking(path,
//                            Buffer.buffer(CodecUtil.compress(new JsonObject().put("patterns", new HashMap<>(items)).put("pattern.plugins", new HashMap<>(patternIds)).encode().getBytes())));
//                }
//                catch (Exception exception)
//                {
//                    System.out.println(Arrays.toString(exception.getStackTrace()));
//                }
//                promise.complete();
//            });
//        }
//    }
    private String mask(String message)
    {
        for (var entry : MASKING_PATTERNS.entrySet())
        {
            message = entry.getKey().matcher(message).replaceAll(entry.getValue());
        }
        return message;
    }
}


