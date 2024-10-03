import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Node {
    private final HashMap<String, Node> childNodeKeys = new HashMap<>();
    private final List<Integer> clusterIds = new ArrayList<>();

    public List<Integer> getClusterIds() {
        return clusterIds;
    }

    public HashMap<String, Node> getChildNodeKeys() {
        return childNodeKeys;
    }

}
