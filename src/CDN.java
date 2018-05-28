import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by WSPN on 2018/5/10.
 */
public class CDN {

    public enum CACHE_STRATEGY {LRU,LFU};
    public static CACHE_STRATEGY strategy = CACHE_STRATEGY.LRU;
    public static double CACHE_RATIO = 0.1;
    public static int CACHE_NUM = (int) (Main.NUM_OF_MOVIES * CACHE_RATIO);
    public static int INIT_NUM = Main.NUM_OF_REQUESTS/200;
    public static long UPDATE_PERIOD = 20000;

    public static int NATIVE_COST = 20;

    //LFU
    public LFUAgingMap<String,Integer> lfuAgingMap;


    //LRU
    public LRULinkedHashMap<String,Integer> req;
    public HashSet<String> cache;
    public ArrayList<Double> hit_ratio_rec;
    public ArrayList<Double> hit_outer_ratio_rec;
    public ArrayList<Double> gain_rec;
    public ArrayList<Integer> handle_outer_rec;
    public String filename;

    public double gain;
    public long counter;
    public long hit_counter;
    public long hit_outer_counter;
    public long handle_outer_counter;
    public int cache_update_cnt;

    public String id;

    public GateWay gw;
    public int local_delay;
    public int outer_delay;


    public CDN(String id,String filename,GateWay gw){
            lfuAgingMap = new LFUAgingMap<>(Main.NUM_OF_MOVIES);
            req = new LRULinkedHashMap<>(16,0.75f,true,Main.NUM_OF_MOVIES);
            cache = new HashSet<>();
            hit_ratio_rec = new ArrayList<>();
            hit_outer_ratio_rec = new ArrayList<>();
            handle_outer_rec = new ArrayList<>();
            gain_rec = new ArrayList<>();

            this.filename = filename;
            this.counter = 0;
            this.hit_outer_counter = 0;
            this.handle_outer_counter = 0;
            this.gw = gw;
            this.id = id;

            cache_update_cnt = 0;
            gw.attach(this);

    }

    public CDN(String id,String filename,GateWay gw,int local_delay,int outer_delay){
        this(id,filename,gw);
        this.local_delay= local_delay;
        this.outer_delay = outer_delay;
    }

    public void initCache(){
        File file = new File(filename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            for(int i=0;i<INIT_NUM;i++){
                String mov_id = br.readLine();
        //        if(i<=1) continue;
                if(strategy == CACHE_STRATEGY.LRU){
                    req.put(mov_id,-1);
                }else {
                    lfuAgingMap.put(mov_id,-1);
                }

            }
            //TODO
            if(strategy == CACHE_STRATEGY.LRU){
                Stack<Map.Entry> stack = new Stack<>();
                for(Map.Entry entry: req.entrySet()){
                    stack.push(entry);
                }
                for(int i = 0;i<CACHE_NUM;i++){
                    cache.add((String)stack.pop().getKey());
                }
            }else {
                List<Map.Entry<String, LFUAgingMap<String,Integer>.HitRate>> list  = lfuAgingMap.sortMapByValue();
                for(int i = 0;i<CACHE_NUM;i++){
                        if(i>=list.size()) break;
                        cache.add(list.get(i).getKey());
                }
            }

            writeCacheToFile(cache);
            syncWithGW();

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(br!=null){
                try{
                    br.close();
                }catch (Exception e){

                }
            }
        }
    }

    public void handleRequest(String mov_id){
        if(mov_id==null){
            return;
        }
        int hit = -1;
        if(cache.contains(mov_id)){
            hit = 0;
        }else {
            hit = Integer.valueOf(requireFromGW(mov_id));
        }
        if(hit>0) {
      //      System.out.println("hit online");
            hit_outer_counter++;
        }
        if(hit != -1){
            hit_counter++;
            if(hit == 0) hit = Integer.valueOf(id);
            gain += fGain(1,gw.graph,Integer.valueOf(id),hit);
        }

        if(strategy == CACHE_STRATEGY.LRU){
            req.put(mov_id,hit);
        }else {
            lfuAgingMap.put(mov_id,hit);
        }

        counter++;

    }

    public void handleRequestlocal(String mov_id){
        if(mov_id==null){
            return;
        }
        int hit = -1;
        if(cache.contains(mov_id)){
            hit = 0;
        }else {
            hit = -1;
        }
        if(hit>0) {
            //      System.out.println("hit online");
            hit_outer_counter++;
        }
        if(hit != -1) hit_counter++;
        if(strategy == CACHE_STRATEGY.LRU){
            req.put(mov_id,hit);
        }else {
            lfuAgingMap.put(mov_id,hit);
        }
        counter++;

    }

    synchronized public void handleOuterRequest(String mov_id){
        handle_outer_counter++;
      //  System.out.println(id+"handleOuterRequest  "+mov_id);
        //  hit_counter++;
        //  req.put(mov_id,0);
        //   counter++;
    }

    public String  requireFromGW(String mov_id){
        return gw.isHit(mov_id);
    }

    synchronized public void updateCache(){

        cache_update_cnt++;
        HashSet<String> set = new HashSet<>();

        if(strategy == CACHE_STRATEGY.LRU){
            Stack<Map.Entry> stack = new Stack<>();
            for(Map.Entry entry: req.entrySet()){
                stack.push(entry);
            }
            while(set.size() <= CACHE_NUM && !stack.isEmpty()){
                Map.Entry entry = stack.pop();
                String mov_id = (String) entry.getKey();
                int flag = (int) entry.getValue();
                if(flag == -1){
                    //             System.out.println("CDN"+id+" updating mov_id "+ (String)entry.getKey());
                    set.add(mov_id);
                }else if(flag == 0){
                    //keep this movie
                    set.add(mov_id);
                }
            }
        }else {
            List<Map.Entry<String, LFUAgingMap<String,Integer>.HitRate>> list  = lfuAgingMap.sortMapByValue();
            for(int i = 0;i<list.size();i++){
                if (set.size()>=CACHE_NUM) break;
                Map.Entry entry  = list.get(i);
                String mov_id = (String) entry.getKey();
                if(mov_id == null) continue;
                int flag = (int) lfuAgingMap.get(mov_id);
                if(flag == -1){
                    set.add(mov_id);
                }else if(flag == 0){
                    //keep this movie
                    set.add(mov_id);
                }
            }

            //clear lfu cache
          //  lfuAgingMap.clear();
        }

        cache.clear();
        cache = set;
      //  System.out.println(this.id+"cache size "+ set.size());
        writeCacheToFile(cache);
        syncWithGW();
   //     System.out.println("updating cache "+ this.id);
    }



    public void syncWithGW(){
        gw.syncWithCDN(this);
    }

    public double getHitRatio(){
        double hit_ratio = (double) hit_counter/counter;
        double hit_ratio_outer = (double) hit_outer_counter/counter;
//        if(id.equals("1")){
//            System.out.println(id+ " hit_ratio:"+ hit_ratio);
//            System.out.println(id+ " update cache round:"+ cache_update_cnt);
//            System.out.println(id+ " outer_hit_ratio :"+ hit_ratio_outer);
//            System.out.println(id+ " handle_outer_request :"+ handle_outer_counter);
//            System.out.println("_______________________________________");
//        }

        hit_ratio_rec.add(hit_ratio);
        hit_outer_ratio_rec.add(hit_ratio_outer);
        handle_outer_rec.add((int)handle_outer_counter);
        gain_rec.add(gain);

        gain = 0.0;
        counter = 0;
        hit_counter = 0;
        hit_outer_counter = 0;
        handle_outer_counter = 0;

        return hit_ratio;
    }

    public double getHitRatioAVG(){
      //  if(counter!=0) hit_ratio_rec.add(getHitRatio());
        double sum = 0.0;
        for(double i:hit_ratio_rec) sum+=i;
        double hit_ratio_avg = (double)(sum/hit_ratio_rec.size());
        sum = 0;
        for(double i:hit_outer_ratio_rec) sum+=i;
        double hit_outer_ratio_avg = (double)(sum/hit_outer_ratio_rec.size());
        sum = 0;
        for(int i:handle_outer_rec) sum+=i;
        int handle_avg = (int)(sum/handle_outer_rec.size());
        sum = 0;
        for (double i:gain_rec) sum += i;
        double gain_avg = (double) sum/gain_rec.size();
        DecimalFormat df = new DecimalFormat("0.00");
        System.out.println( id + "  ends:" + df.format(hit_ratio_avg) + "  "+ df.format(hit_outer_ratio_avg)+"  "+gain_avg);
     //   System.out.println(df.format(hit_outer_ratio_avg));
     //   System.out.println(df.format(hit_ratio_avg));
        return hit_ratio_avg;
    }

    public void writeCacheToFile(Set<String> cache){
        String suffix = id+String.valueOf(cache_update_cnt);
        File file = new File("cache"+suffix);
        try {
            FileWriter fw = new FileWriter(file);
            for(String str :cache){
                fw.write(str+"\n");
            }
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void adjustRedundancy(HashMap<String,Integer> tmpCache,int[][] graph){

//        SrcFile[]  candidates = new SrcFile[cache.size()];
//
//        Iterator<String> iterator = cache.iterator();
//        int i = 0;
//        while (iterator.hasNext() && i<cache.size()){
//            String tmp = iterator.next();
//            candidates[i++] = new SrcFile(tmp,lfuAgingMap.getHitCount(tmp));
//        }
//
//        Arrays.sort(candidates);
//        List<Map.Entry<String, LFUAgingMap<String,Integer>.HitRate>> list = lfuAgingMap.sortMapByValue();
//
//
//        int endPointer = candidates.length-1;
//
//        for(int cnt=0;cnt<list.size() && cnt < endPointer;cnt++){
//            Map.Entry entry = list.get(cnt);
//     //       if(cnt>=CACHE_NUM) break;
//            String v_id = (String)entry.getKey();
//            if(cache.contains(v_id)) continue;
//            else {
//                String u_id = candidates[endPointer].getId();
//                double local_hit_gain_v = fGain(lfuAgingMap.km.get(v_id).hitCount,graph,Integer.valueOf(id),Integer.valueOf(id));
//                double outer_hit_gain_v = fGain(lfuAgingMap.km.get(v_id).hitCount,graph,Integer.valueOf(id),tmpCache.get(v_id));
//                double loss_u = 0.0;
//                for(int k=1;k<=Main.NUM_OF_CDN;k++){
//                    int hc = gw.cdns.get(Integer.toString(k)).lfuAgingMap.getHitCount(u_id);
//                    loss_u += fGain(hc,graph,k,Integer.valueOf(id));
//                }
//                if(local_hit_gain_v-outer_hit_gain_v >= loss_u){
//                    // replace the old cache
//                  //  System.out.println(id+" replace cache file from " + u_id +" to " + v_id);
//                    cache.add(v_id);
//                    cache.remove(u_id);
//                    endPointer--;
//                }else {
//                 //   System.out.println(id+" keep file uncached " + v_id);
//                }
//            }
//        }
        cache_update_cnt++;
        writeCacheToFile(cache);
    }

    public double fGain(int hitCount,int[][] graph,int native_id,int hit_id){
        int outer_cost = graph[native_id][hit_id];
        double factor = 1.0;
        double gain = factor*hitCount/(1+outer_cost/local_delay);
        return gain;
    }

}
