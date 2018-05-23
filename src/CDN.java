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

    //LFU
    public LFUAgingMap<String,Integer> lfuAgingMap;


    //LRU
    public LRULinkedHashMap<String,Integer> req;
    public HashSet<String> cache;
    public ArrayList<Double> hit_ratio_rec;
        public ArrayList<Double> hit_outer_ratio_rec;
        public ArrayList<Integer> handle_outer_rec;
        public String filename;

        public long counter;
        public long hit_counter;
        public long hit_outer_counter;
        public long handle_outer_counter;
        public int cache_update_cnt;

        public String id;

        public GateWay gw;

    public CDN(String id,String filename,GateWay gw){
            lfuAgingMap = new LFUAgingMap<>(Main.NUM_OF_MOVIES);
            req = new LRULinkedHashMap<>(16,0.75f,true,Main.NUM_OF_MOVIES);
            cache = new HashSet<>();
            hit_ratio_rec = new ArrayList<>();
            hit_outer_ratio_rec = new ArrayList<>();
            handle_outer_rec = new ArrayList<>();

            this.filename = filename;
            this.counter = 0;
            this.hit_outer_counter = 0;
            this.handle_outer_counter = 0;
            this.gw = gw;
            this.id = id;

            cache_update_cnt = 0;
        gw.attach(this);

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
        if(hit != -1) hit_counter++;

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
        DecimalFormat df = new DecimalFormat("0.00");
      //  System.out.println( id + "  ends:" + df.format(hit_ratio_avg) + "  "+ df.format(hit_outer_ratio_avg)+"  "+handle_avg);
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

}
