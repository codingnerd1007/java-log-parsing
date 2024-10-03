import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class Main {
    public static void main(String[] args) throws Exception {
        String log;
        Set<Integer> results = new HashSet<>();
//        LogPatternCacheStore logPatternCacheStore = LogPatternCacheStore.getStore();
        LogPatternCacheStoreModified logPatternCacheStore = LogPatternCacheStoreModified.getStore();
        logPatternCacheStore.initStore();
        File logFile = new File("/home/rahil/TASKS/R&D/compliance/checking/untitled/resources/forti.log");
        String fileName = logFile.getName().split("\\.")[0];
        BufferedReader reader = new BufferedReader(new FileReader(logFile));
        Instant timeStart = Instant.now();
        long logCount = 0L;
        while((log = reader.readLine()) != null) {
            int tempResult = logPatternCacheStore.detectPattern(fileName, log);
//            int tempResult = logPatternCacheStore.detectPatternModified(fileName, log);
            System.out.println(tempResult);
            results.add(tempResult);
            logCount++;
        }
        Instant timeEnd = Instant.now();
        reader.close();
        System.out.println("output: " + results);

        System.out.println("Execution time: " + Duration.between(timeStart, timeEnd).toMillis()/1000);


//        Map<String, Integer> items = logPatternCacheStore.getItems();//it will have pattern as key and value will be patternId
//        Map<String, String> patternIds = logPatternCacheStore.getPatternIds();//pluginid and comma separated patternIds
//        Map<Integer, String> patterns = logPatternCacheStore.getPatterns(); // pattern id with pattern
//
//        System.out.println("====================items====================");
//        items.forEach((k, v) -> System.out.println(k + " ---> " + v));
//        System.out.println("===================patterns====================");
//        patterns.forEach((k, v) -> System.out.println(k + " ---> " + v));
//        System.out.println("==================patternIds=====================");
//        patternIds.forEach((k, v) -> System.out.println(k + " ---> " + v));
//        System.out.println("==================================================");
//        System.out.println("total items: " + items.size());
//        System.out.println("total patterns: " + patterns.size());
//        System.out.println("total patternIds: " + patternIds.size());
//        System.out.println("log parsed: " + logCount);
//        System.out.println("Execution time: " + Duration.between(timeStart, timeEnd).toMillis());
    }
}
