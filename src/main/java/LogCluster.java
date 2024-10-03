import java.util.List;

public class LogCluster {
    private List<String> logTemplateTokens;

    private int clusterId;

    private int size;

    LogCluster(List<String> logTemplateTokens, int clusterId) {
        this.logTemplateTokens = logTemplateTokens;
        this.clusterId = clusterId;
        this.size = 1;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
    public int getClusterId() {
        return clusterId;
    }

    public List<String> getLogTemplateTokens() {
        return logTemplateTokens;
    }


}
