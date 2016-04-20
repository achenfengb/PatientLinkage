/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patientlinkage.parties;

import patientlinkage.GarbledCircuit.PatientLinkageWssGadget;
import patientlinkage.DataType.PatientLinkage4GadgetWsInputs;
import patientlinkage.DataType.PatientLinkageWssOutput;
import cv.CVCompEnv;
import flexsc.CompEnv;
import flexsc.CompPool;
import flexsc.Mode;
import flexsc.Party;
import gc.GCEva;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import patientlinkage.DataType.PatientLinkage;
import patientlinkage.Util.Util;
import pm.PMCompEnv;

/**
 *
 * @author cf
 * @param <T>
 */
public class EnvWss <T> extends network.Client{
    String addr;
    int port;
    Mode mode;
    boolean[][][] bin_b;
    boolean[][] Ws_b;
    boolean[]threshold_b;
    int len_a;
    ArrayList<PatientLinkage> linkage;
    ArrayList<String> PartyA_IDs;
    ArrayList<String> PartyB_IDs;
    int numOfTasks;

    public EnvWss(String addr, int port, Mode mode, int numOfTasks, boolean[][][] bin_b, boolean[][] Ws_b, boolean[] threshold_b, int len_a, ArrayList<String> PartyB_IDs) {
        this.addr = addr;
        this.port = port;
        this.mode = mode;
        this.bin_b = bin_b;
        this.Ws_b = Ws_b;
        this.threshold_b = threshold_b;
        this.len_a = len_a;
        this.PartyB_IDs = PartyB_IDs;
        this.numOfTasks = numOfTasks;
    }

    public void implement() {
        int[][] Range0 = Util.linspace(0, this.len_a, numOfTasks);
        
        try {
            connect(addr, port);
            System.out.println("connected with the generator!");

            CompEnv<T> eva = null;

            if (null != mode) switch (mode) {
                case REAL:
                    eva = (CompEnv<T>) new GCEva(is, os);
                    break;
                case VERIFY:
                    eva = (CompEnv<T>) new CVCompEnv(is, os, Party.Bob);
                    break;
                case COUNT:
                    eva = (CompEnv<T>) new PMCompEnv(is, os, Party.Bob);
                    break;
                default:
                    break;
            }
            
            //input
            System.out.println("initializing patient linkage circuit ...");
            Object[] inputs = new Object[this.numOfTasks];
            boolean[][][] bin_a = Util.generateDummyArray(bin_b, len_a);
            boolean[][] Ws_a = new boolean[this.Ws_b.length][this.Ws_b[0].length];
            boolean[] threshold_a = new boolean[this.threshold_b.length];
            
            PatientLinkage4GadgetWsInputs.resetBar();
            PatientLinkage4GadgetWsInputs.all_progresses = len_a + this.bin_b.length * this.numOfTasks;
            for(int i = 0; i < this.numOfTasks; i++){
                PatientLinkage4GadgetWsInputs<T> tmp0 = new PatientLinkage4GadgetWsInputs<>(Arrays.copyOfRange(bin_a, Range0[i][0], Range0[i][1]), Ws_a, threshold_a, eva, "Alice", i);         
                PatientLinkage4GadgetWsInputs<T> tmp1 = new PatientLinkage4GadgetWsInputs<>(this.bin_b, Ws_b, threshold_b, eva, "Bob", i); 
                inputs[i] = new Object[]{tmp0, tmp1};
            }
            System.out.println(String.format("[%s]%d%%      \r", PatientLinkage4GadgetWsInputs.progress(100), 100));
            os.flush();
            //end
            
            //compute
            System.out.println("computing patient linkage circuit ...");
            PatientLinkageWssGadget.resetBar();
            PatientLinkageWssGadget.all_progresses = bin_a.length * len_a;
            CompPool<T> pool = new CompPool<>(eva, this.addr, this.port+1);
            Object[] result = pool.runGadget(new PatientLinkageWssGadget(), inputs);
            System.out.println(String.format("[%s]%d%%      \r", PatientLinkageWssGadget.progress(100), 100));
            
            Object[] result1 = new Object[result.length];
            Object[] result2 = new Object[result.length];
            
            for(int i = 0; i < result.length; i++){
                result1[i] = ((PatientLinkageWssOutput)result[i]).getA();
                result2[i] = ((PatientLinkageWssOutput)result[i]).getB();
            }
            T[][] d1 = Util.<T>unifyArray(result1, eva, len_a);
            T[][][] d2 = Util.<T>unifyArray1(result2, eva, len_a);
            os.flush();
            //end
            
            //Output
            System.out.println("GB protocol completed!");
            for (T[] d11 : d1) {
                eva.outputToAlice(d11);
            }
            os.flush();
            for (T[][] d21 : d2) {
                for (T[] d211 : d21) {
                    eva.outputToAlice(d211);
                }
            }
            os.flush();
            //end
            ObjectInputStream ois = new ObjectInputStream(is);
            linkage = (ArrayList<PatientLinkage>)ois.readObject();
            ObjectOutputStream oos = new ObjectOutputStream(os);
            this.PartyA_IDs = (ArrayList<String>)ois.readObject();
            oos.writeObject(this.PartyB_IDs);
            oos.flush();
            pool.finalize();
            
            disconnect();

        } catch (Exception ex) {
            Logger.getLogger(Env.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public ArrayList<PatientLinkage> getLinkage() {
        return linkage;
    }

    public ArrayList<String> getPartyA_IDs() {
        return PartyA_IDs;
    }

}
