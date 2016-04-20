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
import gc.GCGen;
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
public class GenWss <T> extends network.Server{

    int port;
    Mode mode;
    boolean[][][] bin_a;
    boolean[][] Ws_a;
    boolean[] threshold_a;
            
    int len_b;
    boolean[][] z1;
    boolean[][][] z2;
    int[][] z3;
    ArrayList<PatientLinkage> linkage;
    ArrayList<String> PartyA_IDs;
    ArrayList<String> PartyB_IDs;

    int numOfTasks;

    public GenWss(int port, Mode mode, int numOfTasks, boolean[][][] bin_a, boolean[][] Ws_a, boolean[] threshold_a, int len_b, ArrayList<String> PartyA_IDs) {
        this.port = port;
        this.mode = mode;
        this.bin_a = bin_a;
        this.Ws_a = Ws_a;
        this.threshold_a = threshold_a;
        this.len_b = len_b;
        this.z1 = new boolean[bin_a.length][len_b];
        this.z2 = new boolean[bin_a.length][len_b][];
        this.z3 = new int[bin_a.length][len_b];
        this.PartyA_IDs = PartyA_IDs;
        
        this.numOfTasks = numOfTasks;
    }

    public void implement() {
        int[][] Range0 = Util.linspace(0, this.bin_a.length, numOfTasks);

        try {
            listen(port);
            System.out.println("connected with the evaluator!");
            
            CompEnv<T> gen = null;

            if (null != mode) switch (mode) {
                case REAL:
                    gen = (CompEnv<T>) new GCGen(is, os);
                    break;
                case VERIFY:
                    gen = (CompEnv<T>) new CVCompEnv(is, os, Party.Alice);
                    break;
                case COUNT:
                    gen = (CompEnv<T>) new PMCompEnv(is, os, Party.Alice);
                    break;
                default:
                    break;
            }

            System.out.println("initializing patient linkage circuit ...");
            //input
            Object[] inputs = new Object[this.numOfTasks];
            boolean[][][] bin_b = Util.generateDummyArray(bin_a, len_b);
            boolean[][] Ws_b = new boolean[this.Ws_a.length][this.Ws_a[0].length];
            boolean[] threshold_b = new boolean[this.threshold_a.length];

            PatientLinkage4GadgetWsInputs.resetBar();
            PatientLinkage4GadgetWsInputs.all_progresses = this.bin_a.length + len_b * this.numOfTasks;
            for (int i = 0; i < this.numOfTasks; i++) {
                PatientLinkage4GadgetWsInputs<T> tmp0 = new PatientLinkage4GadgetWsInputs<>(Arrays.copyOfRange(bin_a, Range0[i][0], Range0[i][1]), Ws_a, threshold_a, gen, "Alice", i);
                PatientLinkage4GadgetWsInputs<T> tmp1 = new PatientLinkage4GadgetWsInputs<>(bin_b, Ws_b, threshold_b, gen, "Bob", i);
                inputs[i] = new Object[]{tmp0, tmp1};
            }
            System.out.println(String.format("[%s]%d%%      \r", PatientLinkage4GadgetWsInputs.progress(100), 100));
            os.flush();

            //compute
            System.out.println("computing patient linkage circuit ...");
            PatientLinkageWssGadget.resetBar();
            PatientLinkageWssGadget.all_progresses = bin_a.length * len_b;
            CompPool<T> pool = new CompPool<>(gen, "localhost", this.port + 1);
            Object[] result = pool.runGadget(new PatientLinkageWssGadget(), inputs);
            System.out.println(String.format("[%s]%d%%      \r", PatientLinkageWssGadget.progress(100), 100));

            Object[] result1 = new Object[result.length];
            Object[] result2 = new Object[result.length];

            for (int i = 0; i < result.length; i++) {
                result1[i] = ((PatientLinkageWssOutput) result[i]).getA();
                result2[i] = ((PatientLinkageWssOutput) result[i]).getB();
            }
            T[][] d1 = Util.<T>unifyArray(result1, gen, this.bin_a.length);
            T[][][] d2 = Util.<T>unifyArray1(result2, gen, this.bin_a.length);

            os.flush();
            //end

            System.out.println("GB protocol completed! Prepare for output...");
            //Output
            
            for (int i = 0; i < d1.length; i++) {
                z1[i] = gen.outputToAlice(d1[i]);
            }
            os.flush();
            for(int i = 0; i < d2.length; i++){
                for(int j = 0; j < d2[i].length; j++){
                    z2[i][j] = gen.outputToAlice(d2[i][j]);
                    z3[i][j] = Util.toInt(z2[i][j]);
                }
            }
            os.flush();
            //end
            linkage = new ArrayList<>();
            for (int i = 0; i < d1.length; i++) {
                for (int j = 0; j < d1[i].length; j++) {
                    if (z1[i][j]) {
                        linkage.add(new PatientLinkage(i, j, ((float)z3[i][j])/2));
                        //System.out.println(i + " -> " + j + ": " + z3[i][j]);
                    }
                }
            }

            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(this.linkage);
            oos.flush();
            oos.writeObject(this.PartyA_IDs);
            oos.flush();
            ObjectInputStream ois = new ObjectInputStream(is);
            this.PartyB_IDs = (ArrayList<String>)ois.readObject();
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

    public ArrayList<String> getPartyB_IDs() {
        return PartyB_IDs;
    }

    
}
