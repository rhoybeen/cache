import java.util.*;

/**
 * Created by WSPN on 2018/5/10.
 */
public class GateWay {
    public Set<String> cache_set;
    public HashMap<String,CDN> cdns;
    public HashMap<String,boolean[]> cache;
    public boolean re_flag = false;
    public int[][] graph;
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

    public synchronized void updateCache(){

        //generate L(M)
        ArrayList<int[]> list = new ArrayList<>();

        for(int i=1;i<=Main.NUM_OF_MOVIES;i++){
            int[] reqs = new int[Main.NUM_OF_CDN+2];
            reqs[0] = i;
            for(int j=0;j<Main.NUM_OF_CDN;j++){
                int tmp = Integer.MIN_VALUE;
                for(Map.Entry entry:cdns.entrySet()){
                    CDN cdn = (CDN) entry.getValue();
                    int cdn_id = Integer.valueOf(cdn.id);
                    LFUAgingMap.HitRate hitRate = cdn.lfuAgingMap.km.get(Integer.toString(i));
                    int hitCount = 0;
                    if(hitRate!=null)
                        hitCount = hitRate.hitCount;
                    reqs[cdn_id]  = hitCount;
                    tmp = Math.max(tmp,hitCount);
                }
                reqs[Main.NUM_OF_CDN+1] = tmp;
            }
            list.add(reqs);
        }
        Collections.sort(list, new Comparator<int[]>() {
            @Override
            public int compare(int[] o1, int[] o2) {
                return o1[Main.NUM_OF_CDN+1]-o2[Main.NUM_OF_CDN+1];
            }
        });

        //clear old cache
        for(Map.Entry entry:cdns.entrySet()){
            CDN cdn = (CDN) entry.getValue();
            cdn.cache.clear();
        }


        int[] cache_sizes = new int[Main.NUM_OF_CDN+1];
        HashMap<String,Integer> tmp_cache = new HashMap<>();

        for(int[] arr: list){

            if(isCacheFull(cache_sizes)) break;
            int[] copy = Arrays.copyOfRange(arr,1,arr.length-1);
            Arrays.sort(copy);
            int f = 1;
            int index = Arrays.binarySearch(arr,1,arr.length-1,copy[f]);
            while(isCacheFull(cache_sizes,index)){
                f++;
                index = Arrays.binarySearch(arr,1,arr.length-1,copy[f]);
            }
            cache_sizes[index]++;
            cdns.get(String .valueOf(index)).cache.add(String .valueOf(arr[0]));
            tmp_cache.put(String .valueOf(arr[0]),index);
        }
        if(graph == null) generateGraph();
        for(CDN cdn:cdns.values()){
            cdn.adjustRedundancy(tmp_cache,graph);
        }

    }

    public boolean isCacheFull(int[] sizes,int n){
        return sizes[n] >= CDN.CACHE_NUM;
    }

    public boolean isCacheFull(int[] sizes){
        boolean flag = true;
        for(int i=1;i<sizes.length;i++)
            flag &= isCacheFull(sizes,i);
        return flag;
    }

    public boolean generateGraph(){
        int num = Main.NUM_OF_CDN;
        if(cdns.size() != num) return false;
        graph = new int[num+1][num+1];
        for(int i=1;i<=num;i++){
            for(int j=1;j<=num;j++){
                int od = cdns.get(Integer.toString(i)).outer_delay + cdns.get(Integer.toString(j)).outer_delay;
                if(i==j) od = 0;
                graph[i][j] = od;
            }
        }
        return true;
    }
}
