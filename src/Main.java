import org.apache.commons.math3.distribution.ZipfDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import sun.rmi.runtime.Log;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;

import static java.lang.Thread.sleep;

public class Main {

    //zipf distribution params
    public static double ZIPF_C = 0.164093;
    public static double ZIPF_P = 0.1;


    public static int NUM_OF_MOVIES = 3000;
    public static int INTERFERENCE = 100;
    public static int NUM_OF_REQUESTS = 200000;
    public static int MUL_FACTOR = 100000;
    public static int NUM_OF_CDN = 1;

    public static int dis_nativ = 20;
    public static int[] dis_others = new int[]{10,15,20,25,30,35,40,45,50,55} ;


    static CyclicBarrier c = null;

    public static void main(String[] args) {


        double[] ratios = {0.02};
        int outer_delay = 10;
 //       int[] mul = {1,5,10,15,20};

            for(double ratio: ratios){
                NUM_OF_CDN = 4;
                CDN.CACHE_NUM = (int) (NUM_OF_MOVIES * ratio);
                CDN.strategy  = CDN.CACHE_STRATEGY.LFU;
      //          CDN.INIT_NUM = CDN.CACHE_NUM * m;
                c = new CyclicBarrier(NUM_OF_CDN+1);
                GateWay gw = new GateWay();
                CDN cdn1 = new CDN("1","req01.dat",gw,10,outer_delay);
                CDN cdn2 = new CDN("2","req02.dat",gw,10,outer_delay);
                CDN cdn3 = new CDN("3","req03.dat",gw,10,outer_delay);
                CDN cdn4 = new CDN("4","req04.dat",gw,10,outer_delay);
                cdn1.initCache();
                cdn2.initCache();
                cdn3.initCache();
                cdn4.initCache();
                gw.generateGraph();
                Thread thread = new Thread(new Client(cdn1,c));
                Thread thread2 = new Thread(new Client(cdn2,c));
                Thread thread3 = new Thread(new Client(cdn3,c));
                Thread thread4 = new Thread(new Client(cdn4,c));

                Thread daemon = new Thread(new Monitor(c,gw));
                daemon.setDaemon(true);
                daemon.start();

                thread.start();
                thread2.start();
                thread3.start();
                thread4.start();
                try{
                    sleep(4000);
                }catch (Exception e){
                    e.printStackTrace();
                }
                calcRedundancy(ratio);
                //   System.out.println(" ——————————————————————");
            }




    }

    public static void calcRedundancy(double cache_ratio){
    //    System.out.println("______________________"+cache_ratio);
        DecimalFormat df = new DecimalFormat("0.00");
        for(int i = 0;i<10;i++){
            int[][] caches = new int[NUM_OF_CDN][CDN.CACHE_NUM+1];
            for(int j=1;j<=NUM_OF_CDN;j++){
                String suffix = String.valueOf(j)+String.valueOf(i);
                File file = new File("cache"+suffix);
                try{
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String tmp = br.readLine();
                    int cnt = 0;
                    while (tmp!=null){
                        caches[j-1][cnt++] = Integer.valueOf(tmp);
                        tmp = br.readLine();
                    }
                    br.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            double sum = 0;
            for(int j=0;j<NUM_OF_CDN;j++){
                int tmp = 0;
                for(int z = 0;z<caches[j].length;z++){
                    boolean flag = false;
                    for(int y=0;y<NUM_OF_CDN;y++){
                        if(y == j) continue;
                        for(int x = 0;x<caches[y].length;x++){
                            if(caches[j][z] == caches[y][x]){
                                flag = true;
                                break;
                            }
                        }
                        if(flag) break;
                    }
                    if(flag) tmp++;
                }
                double red = (double) tmp / caches[j].length;
                sum+=red;
                if(j==0)     System.out.println("Round "+ String.valueOf(i)+" cdn no " + String.valueOf(j+1)+ "  red is:" +df.format(red));
            }
 //           if(i==9) System.out.println(df.format(sum/NUM_OF_CDN));
        }
    }
    public static void generateRequests(){
        // zipf distribution
        DecimalFormat df = new DecimalFormat("###.0");
        double[] probs = new double[NUM_OF_MOVIES+1];
        double[] zones = new double[NUM_OF_MOVIES+1];
        int[] other = new int[INTERFERENCE+1];
        double cnt = 0;
        for(int i =1;i<probs.length;i++){
            double pro = ZIPF_C/(Math.pow(i,ZIPF_P+1))*MUL_FACTOR;
            probs[i] = pro;
//            pro = Math.round(pro);
//            count += pro;
            cnt += pro;
            zones[i] = cnt;
//            System.out.println(df.format(pro));
//            System.out.println(df.format(cnt));
        }

        //generate requests
        Random random = new Random();

        for(int i = 1;i<other.length;i++){
            other[i] = i;
        }
        for(int i = 1;i<other.length;i++){
            int length = other.length-i;
            int r = 0;
            while(r==0){
                r = random.nextInt(length+1);
            }
            int tmp = other[length];
            other[length] = other[r];
            other[r] = tmp;
            length--;
        }

        File file = new File("requests1.dat");
        File file2 = new File("requests2.dat");
        try{

            FileWriter fw = new FileWriter(file);
            fw.write("ZIPF_C = 0.164093 \n");
            fw.write("ZIPF_P = 0.1 \n");

            FileWriter fw2 = new FileWriter(file2);
            fw2.write("ZIPF_C = 0.164093 \n");
            fw2.write("ZIPF_P = 0.1 \n");

            for(int i=0;i<NUM_OF_REQUESTS;i++){
                int tmp = 0;
                while(tmp==0){
                    tmp = random.nextInt(MUL_FACTOR);
                }
                int index = binarySearch(zones,tmp);
                System.out.println("printint No."+ String.valueOf(i)+ "  "+ String.valueOf(index));
                int index2 = index>=INTERFERENCE?index:other[index];
                fw.write((Integer.toString(index))+"\n");
                fw2.write((Integer.toString(index2))+"\n");

            }
            fw.close();
            fw2.close();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    public static void generateRequestsf(){
        // zipf distribution
        ZipfDistribution zipfDistribution = new ZipfDistribution(NUM_OF_MOVIES,0.7);
        ZipfDistribution zipfDistribution2 = new ZipfDistribution(NUM_OF_MOVIES,0.65);


        DecimalFormat df = new DecimalFormat("###.0");

        double[] probs = new double[NUM_OF_MOVIES+1];
        int[] other = new int[INTERFERENCE+1];
 //       double[] probs2 = new double[NUM_OF_MOVIES+1];

  //      int[] other = new int[INTERFERENCE+1];
 //       double cnt = 0;
        for(int i =1;i<probs.length;i++){
            double pro = zipfDistribution.probability(i)*MUL_FACTOR;
            probs[i] = pro;
        }

        Random random = new Random();

        for(int i = 1;i<other.length;i++){
            other[i] = i;
        }
        for(int i = 1;i<other.length;i++){
            int length = other.length-i;
            int r = 0;
            while(r==0){
                r = random.nextInt(length+1);
            }
            int tmp = other[length];
            other[length] = other[r];
            other[r] = tmp;
            length--;
        }

        String suffix = "03";
        File file = new File("req"+suffix+".dat");
        File file2 = new File("probs"+suffix+".dat");

        String suffix2 = "04";
        File file3 = new File("req"+suffix2+".dat");
        File file4 = new File("probs"+suffix2+".dat");
        try{

            FileWriter fw = new FileWriter(file);
            FileWriter fw2 = new FileWriter(file2);
            FileWriter fw3 = new FileWriter(file3);

            for(double p:probs){
                fw2.write(Double.toString(p)+"\n");
            }


            for(int i=0;i<NUM_OF_REQUESTS;i++){
                fw.write(String.valueOf(zipfDistribution.sample())+"\n");
                int index = zipfDistribution2.sample();
                int index2 = index>=INTERFERENCE?index:other[index];
                fw3.write(String.valueOf(index2)+"\n");
            }
            fw.close();
            fw2.close();
            fw3.close();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    public static int binarySearch(double[] zones,int num){

        int start = 1;
        int end = zones.length-1;
        int index = (start+end)/2;
        while (start<end){
            if(zones[index]>=num){
                if(zones[index-1]<num) return index;
                end = index;
            }else if(zones[index]<num){
                if(zones[index+1]>num) return index+1;
                start = index;
            }
            index = (start+end)/2;
        }
        System.out.println("num:"+ num);
        return -1;
    }

    public static int binarySearch(double[] zones,double num){

        int start = 1;
        int end = zones.length-1;
        int index = (start+end)/2;
        while (start<end){
            if(zones[index]>=num){
                if(zones[index-1]<num) return index;
                end = index;
            }else if(zones[index]<num){
                if(zones[index+1]>num) return index+1;
                start = index;
            }
            index = (start+end)/2;
        }
        System.out.println("num:"+ num);
        return -1;
    }

    public static void writeToFile(int[] requests){

    }

}

class Monitor implements Runnable {
    CyclicBarrier c;
    GateWay gw;
    Monitor(CyclicBarrier c,GateWay gw){
        this.gw = gw;
        this.c = c;
    }

    @Override
    public void run() {
        while (true){
            while (c.getNumberWaiting() != Main.NUM_OF_CDN){
                try {
                    sleep(1);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            gw.updateCache();
            try {
                c.await();
                sleep(1);
                c.reset();
                System.out.println("reset cyclic barrier");
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}

class Client implements Runnable { // 实现了Runnable接口，jdk就知道这个类是一个线程
    CDN cdn;
    CyclicBarrier c;
    Client(CDN cdn,CyclicBarrier c) {
        this.cdn = cdn;
        this.c = c;
    }
    public void run() {
        File file = new File(cdn.filename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String mov_id = null;
            for(int i=0;i<CDN.INIT_NUM;i++){
                mov_id = br.readLine();
            }
            while (mov_id!=null){
          //      System.out.println("sending request "+ mov_id);
                mov_id = br.readLine();
                cdn.handleRequest(mov_id);
                if(cdn.counter>=CDN.UPDATE_PERIOD){
            //        cdn.updateCache();
                    cdn.getHitRatio();
                    c.await();
                    sleep(2);
                }

            }
            cdn.getHitRatioAVG();
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
}


