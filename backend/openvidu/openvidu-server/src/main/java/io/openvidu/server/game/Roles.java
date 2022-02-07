package io.openvidu.server.game;

public enum Roles {

    L("L",false,1,1),
    KIRA("KIRA",false,1,1),
    GUARD("GUARD",true,1,1),
    BROADCASTER("BROADCASTER",true,1,1),
    CRIMINAL("CRIMINAL",true,1,2),
    POLICE("POLICE",true,1,3);

    private String jobName;
    private boolean isChange;
    private Integer count;
    private Integer maxCount;

    Roles(String jobName, boolean isChange, Integer count, Integer maxCount) {
        this.jobName = jobName;
        this.isChange = isChange;
        this.count = count;
        this.maxCount = maxCount;
    }

    public String getJobName() {
        return jobName;
    }


    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public boolean isChange() {
        return isChange;
    }
}
