/*
 * Copyright (c) Motadata 2024. All rights reserved.
 */


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class LogPatternCacheStoreModified
{
    private static final LogPatternCacheStoreModified STORE = new LogPatternCacheStoreModified();
    private static final String COMMA_SEPARATOR = ",";
    private static final double MATCHING_SCORE = 0.5;
    private static final String PATTERN_PLACE_HOLDER = "****";
    private static final Map<Pattern, String> MASKING_PATTERNS = new LinkedHashMap<>();
    private final Map<String, Integer> items = new ConcurrentHashMap<>();//it will have pattern as key and value will be patternId
    private final Map<String, String> patternIds = new ConcurrentHashMap<>();//pluginid and comma separated patternIds
    private final Map<Integer, String> patterns = new ConcurrentHashMap<>(); // pattern id with pattern
    private final AtomicInteger patternId = new AtomicInteger();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private LogPatternCacheStoreModified()
    {
    }
    public static LogPatternCacheStoreModified getStore()
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
    private final Cache<Integer, LogCluster> clusterCache = Caffeine.newBuilder().maximumSize(1000).build();
    private final int DEPTH = 4;
    private final int MAX_NODE_DEPTH = DEPTH - 2;
    AtomicInteger clusterCounter = new AtomicInteger();
    private final int MAX_CHILDREN = 100;
    Node rootNode = new Node();

    public void initStore()
    {
        loadPatterns();
        System.out.printf("store %s initialized...%n", this.getClass().getSimpleName());
    }

    public int detectPatternModified(String plugin, String message)
    {
        try
        {
            message = mask(message);

            ArrayList <String> messageList = new ArrayList<>(Arrays.asList(message.split("\\s+")));

            var matchedCluster = treeSearch(plugin, messageList, rootNode);

            if (matchedCluster == null)
            {
                int clusterId = clusterCounter.incrementAndGet();
                matchedCluster = new LogCluster(messageList, clusterId);
                clusterCache.put(clusterId, matchedCluster);
                addSeqToPrefixTree(rootNode, matchedCluster);
            }
            else
            {
                var newTemplate = createTemplate(messageList, matchedCluster.getLogTemplateTokens());
                if (!newTemplate.equals(matchedCluster.getLogTemplateTokens()))
                {
                    matchedCluster.getLogTemplateTokens().clear();
                    matchedCluster.getLogTemplateTokens().addAll(newTemplate);
                }
                matchedCluster.setSize(matchedCluster.getSize() + 1);
                clusterCache.getIfPresent(matchedCluster.getClusterId());
            }
            assert matchedCluster != null;
            return matchedCluster.getClusterId();
        }
        catch (Exception exception)
        {
            System.out.println(Arrays.toString(exception.getStackTrace()));
        }

        return -1;
    }

    private ArrayList<String> createTemplate(List<String> messageList, List<String> logTemplateTokens) {
        assert messageList.size() == logTemplateTokens.size();
        ArrayList<String> newTemplate = new ArrayList<>();
        for (int i = 0; i < logTemplateTokens.size(); i++)
        {
            if(logTemplateTokens.get(i).equals(messageList.get(i)))
            {
                newTemplate.add(logTemplateTokens.get(i));
            }
            else
            {
                newTemplate.add(PATTERN_PLACE_HOLDER);
            }
        }
        return newTemplate;
    }

    private void addSeqToPrefixTree(Node rootNode, LogCluster cluster) {
        var tokenCount = cluster.getLogTemplateTokens().size();
        var tokenCountString = String.valueOf(tokenCount);

        Node firstLayerNode;

        if(!rootNode.getChildNodeKeys().containsKey(tokenCountString))
        {
            firstLayerNode = new Node();
            rootNode.getChildNodeKeys().put(tokenCountString, firstLayerNode);

        }
        else
            firstLayerNode = rootNode.getChildNodeKeys().get(tokenCountString);

        var currentNode = firstLayerNode;

        if(tokenCount == 0)
        {
            currentNode.getClusterIds().add(cluster.getClusterId());
            return;
        }

        var currentDepth = 1;

        for (var token : cluster.getLogTemplateTokens())
        {
            if(currentDepth >= MAX_NODE_DEPTH || currentDepth >= tokenCount)
            {
                ArrayList<Integer> newClusterIds= new ArrayList<>();

                for(var clusterId : currentNode.getClusterIds())
                {
                    if(clusterCache.getIfPresent(clusterId) != null)
                    {
                        newClusterIds.add(clusterId);
                    }
                }
                newClusterIds.add(cluster.getClusterId());
                currentNode.getClusterIds().clear();
                currentNode.getClusterIds().addAll(newClusterIds);
                break;
            }

            if (!currentNode.getChildNodeKeys().containsKey(token))
            {
                if(currentNode.getChildNodeKeys().containsKey(PATTERN_PLACE_HOLDER)) {
                    if (currentNode.getChildNodeKeys().size() < MAX_CHILDREN)
                    {
                        Node newNode = new Node();
                        currentNode.getChildNodeKeys().put(token, newNode);
                        currentNode = newNode;
                    }
                    else
                        currentNode = currentNode.getChildNodeKeys().get(PATTERN_PLACE_HOLDER);
                }
                else {
                    if (currentNode.getChildNodeKeys().size() + 1 < MAX_CHILDREN)
                    {
                        Node newNode = new Node();
                        currentNode.getChildNodeKeys().put(token, newNode);
                        currentNode = newNode;
                    }
                    else if (currentNode.getChildNodeKeys().size() + 1 == MAX_CHILDREN)
                    {
                        Node newNode = new Node();
                        currentNode.getChildNodeKeys().put(PATTERN_PLACE_HOLDER, newNode);
                        currentNode = newNode;
                    }
                    else
                    {
                        currentNode = currentNode.getChildNodeKeys().get(PATTERN_PLACE_HOLDER);
                    }

                }
            }
            else
            {
                currentNode = currentNode.getChildNodeKeys().get(token);
            }
            currentDepth++;
        }
    }

    private LogCluster treeSearch(String plugin, ArrayList<String> messageList, Node rootNode) {
        var tokenCount = messageList.size();
        var currentNode = rootNode.getChildNodeKeys().get(String.valueOf(tokenCount));

        if (currentNode == null)
            return null;

        if (tokenCount == 0)
            return clusterCache.getIfPresent(currentNode.getClusterIds().get(0));

        var currentNodeDepth = 1;

        for (var token : messageList) {
            if (currentNodeDepth >= MAX_NODE_DEPTH)
                break;
            if (currentNodeDepth == tokenCount)
                break;

            var chidNodeKeys = currentNode.getChildNodeKeys();
            currentNode = chidNodeKeys.get(token);
            if (currentNode == null)
                currentNode = chidNodeKeys.get(PATTERN_PLACE_HOLDER);
            if (currentNode == null)
                return null;
            currentNodeDepth++;
        }

        return fastMatch(currentNode.getClusterIds(), messageList, plugin);
    }

    private LogCluster fastMatch(List<Integer> clusterIds, ArrayList<String> messageList, String plugin) {
        LogCluster matchedCluster = null;
        float maxSimilarity = -1;
        int maxParamCount = -1;
        LogCluster maxCluster = null;

        for(var clusterId : clusterIds)
        {
            var cluster = clusterCache.getIfPresent(clusterId);
            if(cluster == null)
                continue;
            var answers = getSeqDistance(cluster.getLogTemplateTokens(), messageList);
            float currentSimilarity = answers.get(0);
            int paramCount = answers.get(1).intValue();
            if (currentSimilarity > maxSimilarity || (currentSimilarity == maxSimilarity && paramCount > maxParamCount))
            {
                maxSimilarity = currentSimilarity;
                maxParamCount = paramCount;
                maxCluster = cluster;
            }
        }
        if (maxSimilarity >= MATCHING_SCORE)
            matchedCluster = maxCluster;

        return matchedCluster;
    }

    private List<Float> getSeqDistance(List<String> logTemplateTokens, ArrayList<String> tokens) {
        assert logTemplateTokens.size() == tokens.size();
        List<Float> answers = new ArrayList<>(2);
        if (logTemplateTokens.size() == 0) {
            answers.add(0, 1.0f);
            answers.add(1, 0f);
            return answers;
        }
        int similarityCount =0 ,paramCount = 0;

        for (int i=0; i < tokens.size(); i++)
        {
            if (logTemplateTokens.get(i).equals(PATTERN_PLACE_HOLDER))
            {
                paramCount++;
                continue;
            }
            if (logTemplateTokens.get(i).equals(tokens.get(i)))
                similarityCount++;

        }
        answers.add(0, (float) similarityCount / logTemplateTokens.size());
        answers.add(1, (float)paramCount);

        return answers;
    }

    public int detectPattern(String plugin, String message)
    {
        try
        {
            message = mask(message);
            var messageTokens = message.split("\\s+");
            if (patternIds.containsKey(plugin))
            {
                var ids = Arrays.stream(patternIds.get(plugin).split(COMMA_SEPARATOR)).toList();
                var builder = new StringBuilder();
                for (var id : ids)
                {
                    builder.setLength(0);
                    var pattern = patterns.get(Integer.parseInt(id));
                    var patternTokens = pattern.split("\\s+");
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


