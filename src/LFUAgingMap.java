import java.util.*;

/**
 * Created by WSPN on 2018/5/22.
 */

public class LFUAgingMap<K, V> extends HashMap<K, V> {
    private static final int DEFAULT_MAX_SIZE = 3;
    private int maxSize = DEFAULT_MAX_SIZE;
    Map<K, HitRate> km = new HashMap<K, HitRate>();
    List<Map.Entry<K,HitRate>> entryList  = null;

    public LFUAgingMap() {
        this(DEFAULT_MAX_SIZE);
    }

    public LFUAgingMap(int maxSize) {
        super(maxSize);
        this.maxSize = maxSize;
    }

    public List<Entry<K,HitRate>> sortMapByValue(){
        entryList = new ArrayList<Entry<K, HitRate>>(km.entrySet());
        Collections.sort(entryList,new HitRateComparator());
        return entryList;
    }

    @Override
    public V get(Object key) {
        V v = super.get(key);
        if (v != null) {
            HitRate hitRate = km.get(key);
            hitRate.hitCount += 1;
            hitRate.atime = System.nanoTime();
        }
        return v;
    }

    @Override
    public V put(K key, V value) {
        while (km.size() >= maxSize) {
            K k = getLFUAging();
            km.remove(k);
            this.remove(k);
        }
        V v = super.put(key, value);
        if(km.get(key) == null){
            km.put(key, new HitRate(key, 1, System.nanoTime()));
        } else{
            this.get(key);
        }
        return v;
    }

    private K getLFUAging() {
        HitRate min = Collections.min(km.values());
        return min.key;
    }

    class HitRate implements Comparable<HitRate> {
        K key;
        Integer hitCount; // 命中次数
        Long atime; // 上次命中时间

        public HitRate(K key, Integer hitCount, Long atime) {
            this.key = key;
            this.hitCount = hitCount;
            this.atime = atime;
        }

        @Override
        public int compareTo(HitRate o) {
            int hr = hitCount.compareTo(o.hitCount);
            return hr != 0 ? hr : atime.compareTo(o.atime);
        }
    }

    //逆序
    class HitRateComparator implements Comparator<Map.Entry<K, HitRate>> {
        @Override
        public int compare(Entry<K, HitRate> o1, Entry<K, HitRate> o2) {
            return o2.getValue().compareTo(o1.getValue());
        }
    }

    public int getHitCount(String id){
        HitRate hitRate= km.get(id);
        return hitRate == null?0:hitRate.hitCount;
    }

}