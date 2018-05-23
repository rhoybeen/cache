import java.util.*;

/**
 * Created by WSPN on 2018/5/10.
 */
public class GateWay {
    public Set<String> cache_set;
    public HashMap<String,CDN> cdns;
    public HashMap<String,boolean[]> cache;
    public boolean re_flag = false;

    public GateWay(){
        cache_set = new HashSet<>();
        cdns = new HashMap<>();
        cache = new HashMap<>();
    }

    public synchronized void attach(CDN cdn){
   //     System.out.println("gate has attached cdn"+cdn.id);
        cdns.put(cdn.id,cdn);
        cache.put(cdn.id,new boolean[Main.NUM_OF_MOVIES+1]);
    }

    public String isHit(String mov_id){
        for(Map.Entry entry:cache.entrySet()){
            boolean[] tmp = (boolean[]) entry.getValue();
            if(tmp[Integer.valueOf(mov_id)]){
                cdns.get(entry.getKey()).handleOuterRequest(mov_id);
                return (String) entry.getKey();
            }
        }
        return "-1";
    }

    public synchronized void syncWithCDN(CDN cdn){
        HashSet<String> cache = cdn.cache;
        this.cache.put(cdn.id,new boolean[Main.NUM_OF_MOVIES+1]);
        for(String str:cache){
            int id = Integer.valueOf(str);
            this.cache.get(cdn.id)[id] = true;
        }
    }

    public synchronized void calcRedundance(){

        int count = 0;
        for(Map.Entry entry:cache.entrySet()){

             for(int i=1;i<=Main.NUM_OF_MOVIES;i++){
                 boolean flag = false;
                 boolean[] tmp = (boolean[]) entry.getValue();
                 if (!tmp[i]) continue;
                 for(Map.Entry other:cache.entrySet()){
                     if(other.getKey().equals((String)entry.getKey())) continue;
                     boolean[] a = (boolean[]) other.getValue();
                     flag = flag || a[i];
                 }
                 if(flag) count++;
             }
             double red = (double) count / CDN.CACHE_NUM;
            System.out.println(entry.getKey()+" redundancy is "+ String.valueOf(red));
            count = 0;
        }
    }
}
