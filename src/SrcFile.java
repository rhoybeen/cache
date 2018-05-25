/**
 * Created by WSPN on 2018/5/25.
 */
public class SrcFile implements Comparable<SrcFile>{
    String id;
    int hitCount;

    public SrcFile(String id,int hitCount){
        this.id = id;
        this.hitCount = hitCount;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        hitCount = hitCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public int compareTo(SrcFile o) {
        return this.hitCount - o.hitCount;
    }
}
