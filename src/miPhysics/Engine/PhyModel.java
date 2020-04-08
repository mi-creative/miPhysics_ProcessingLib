package miPhysics.Engine;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import miPhysics.Utility.SpacePrint;

public class PhyModel extends PhyObject {

    public PhyModel(String name, Medium med) {
        this.setName(name);
        m_lock = new ReentrantLock();
        m_medium = med;
        //System.out.println(m_medium);
    }

    public void init(){
        /* Initialise the stored distances for the springs */
        for(Interaction inter : m_interactions)
            inter.initDistances();
        for(PhyModel m : m_subModels)
            m.init();
    }

    public void clear(){
        // Recursively clear all the sub "objects"...
        for(PhyModel m : m_subModels)
            m.clear();
        // Then clear the list of objects...
        m_subModels.clear();
        m_masses.clear();
        // And clear the list of interactions...
        m_interactions.clear();

        m_massLabels.clear();
        m_subModelLabels.clear();
        m_intLabels.clear();
        m_inOutLabels.clear();
    }

    // Need to check the validity of this... Are mass/interaction phases properly timed when descending recursively?
    public void compute(){
        // reset the space print of this model.
        m_sp.reset();
        m_sp_overall.reset();

        for(Mass m : m_masses) {
            m.compute();
            m_sp.update(m);
        }

        for(PhyModel o : m_subModels)
            o.compute();

        for(Interaction i : m_interactions)
            i.compute();
        for(InOut io : m_inOuts)
            io.compute();
    }

    // Can always apply a force to all the components of a macro object
    protected void applyForce(Vect3D force){
        for(Mass m : m_masses)
            m.applyForce(force);
        for(PhyModel o: m_subModels)
            o.applyForce(force);
    }

    // Will have to define types at some point.
    public massType getType(){
        return m_type;
    }
    protected void setType(massType t){
        m_type = t;
    }


    public Mass getMass(String name){
        if(m_massLabels.get(name) == null){
            System.out.println("Cannot find mass " + name + " in macro " + m_name);
            return null;
        }
        return m_massLabels.get(name);
    }

    public Interaction getInteraction(String name){
        if(m_intLabels.get(name) == null){
            System.out.println("Cannot find interaction " + name + " in macro " + m_name);
            return null;
        }
        return m_intLabels.get(name);
    }

    public PhyModel getPhyModel(String name){
        if(m_subModelLabels.get(name) == null){
            System.out.println("Cannot find sub-macro " + name + " in macro " + m_name);
            return null;
        }
        return m_subModelLabels.get(name);
    }

    public void addPhyModel(PhyModel mac){
        if(mac == null){
            System.out.println("Cannot add sub-macro " + mac + " !!! To " + m_name);
        }
        else {
            if(m_subModelLabels.containsKey(mac.getName())){
                throw new Error("A physical model named " + mac.getName() + "already exists in " + this.getName() + " !");
            }
            else {
                m_subModels.add(mac);
                m_subModelLabels.put(mac.getName(), mac);
            }
        }
    }



    public <T extends Mass> T addMass(String name, T m){
        return addMass(name, m, m_medium);
    }

    public <T extends Mass> T addMass(String name, T m, Medium med){
        if (m_massLabels.get(name) == null){
            try {
                m.setName(name);
                m.setMedium(med);

                // Small trickery to input velocities (either per sample or per second)
                Vect3D pInit = new Vect3D(m.getPos());
                Vect3D vInit = new Vect3D(m.getPosR());
                if (m_velUnits == velUnit.PER_SEC)
                    m.setPosR(pInit.sub(vInit.div(m_simRate)));
                else
                    m.setPosR(pInit.sub(vInit));

                m_masses.add(m);
                m_massLabels.put(name, m);

            } catch (Exception e) {
                System.out.println("Error adding mass module " + name + ": " + e);
                this.m_errorCode = -2;
                return null;
            }
        }
        else {
            System.out.println("Could not create " + m + ", " + name + " label already exists. ");
            this.m_errorCode = -1;
            return null;
        }
        this.m_errorCode = 0;
        return m;
    }



    public <T extends Interaction> T addInteraction(String name, T inter, Mass m1){
        if (inter.getType() == interType.PLANECONTACT3D){
            return addInteraction(name, inter, m1, m1);
        }
        else{
            System.out.println("Missing argument for " + name
                    + " (connects to two masses).");
            this.m_errorCode = -1;
            return null;
        }
    }


    public <T extends Interaction> T addInteraction(String name, T inter, String m_id1){
        Mass m1 = m_massLabels.get(m_id1);
        return addInteraction(name, inter, m1);
    }


    public <T extends Interaction> T addInteraction(String name, T inter, Mass m1, Mass m2) {

        if (m_intLabels.get(name) != null) {
            System.out.println("Cannot create interaction " + name
                    + ": " + name + " interaction already exists. ");
            this.m_errorCode = -1;
            return null;
        }

        if (m1 == null) {
            System.out.println("Cannot create interaction " + name
                    + ": " + m1 + " mass doesn't exist. ");
            this.m_errorCode = -2;
            return null;
        } else if (m2 == null) {
            System.out.println("Cannot create interaction " + name
                    + ": " + m2 + " mass doesn't exist. ");
            this.m_errorCode = -3;
            return null;
        }

        try {
            inter.setName(name);
            inter.connect(m1, m2);
            m_interactions.add(inter);
            m_intLabels.put(name, inter);

        } catch (Exception e) {
            System.out.println("Error adding interaction module " + name + ": " + e);
            this.m_errorCode = -4;
            return null;
        }
        this.m_errorCode = 0;
        return inter;
    }


    private Mass findMassFromAddress(String m_id){
        String[] address;
        String name = m_id;
        Mass m;
        try {
            PhyModel m_ref = this;
            if (m_id.contains("/")) {
                address = m_id.split("/");
                for(int i = 0; i < address.length-1; i++) {
                    if (this.getPhyModel(address[i]) != null)
                        m_ref = this.getPhyModel(address[i]);
                    else
                        throw new Error("Cannot find submodel " + address[i] + " in " + m_ref.getName());
                }
                name = address[address.length-1];
            }

            if(m_ref.massExists(name))
                m = m_ref.getMass(name);
            else
                throw new Error("The mass " + name + " does not exist in " + m_ref.getName());
            return m;
        }
        catch (Error ex){
            System.out.println(ex.getMessage());
            return null;
        }

    }

    public <T extends Interaction> T addInteraction(String name, T inter, String m_id1, String m_id2) {
        try {
            return addInteraction(name, inter, findMassFromAddress(m_id1), findMassFromAddress(m_id2));
        }
        catch (Error ex){
            System.out.println(ex.getMessage());
            return null;
        }

    }


    public <T extends InOut> T addInOut(String name, T mod, Mass m) {
        if (m_inOutLabels.get(name) != null) {
            System.out.println("Cannot create InOut " + name
                    + ": " + name + " extern already exists. ");
            this.m_errorCode = -1;
            return null;
        }
        if (m == null) {
            System.out.println("Cannot create InOut " + name
                    + ": " + m.getName() + " mass doesn't exist. ");
            this.m_errorCode = -2;
            return null;
        }
        try {
            mod.setName(name);
            mod.connect(m);
            m_inOuts.add(mod);
            m_inOutLabels.put(name, mod);
        } catch (Exception e) {
            System.out.println("Error adding InOut module " + name + ": " + e);
            this.m_errorCode = -4;
            return null;
        }
        this.m_errorCode = 0;
        return mod;
    }


    public <T extends InOut> T addInOut(String name, T mod, String m_id) {
        return addInOut(name, mod, findMassFromAddress(m_id));
    }


    public ArrayList<PhyModel> getSubModels(){
        return m_subModels;
    }

    public ArrayList<Mass> getMassList(){
        return m_masses;
    }


    public ArrayList<Interaction> getInteractionList(){
        return m_interactions;
    }

    public int numberOfMassTypes(){
        int nb = m_masses.size();
        for(PhyModel pm : m_subModels)
            nb += pm.numberOfMassTypes();
        return nb;
    }

    public int numberOfInterTypes(){
        int nb = m_interactions.size();
        for(PhyModel pm : m_subModels)
            nb += pm.numberOfInterTypes();
        return nb;
    }

    public int getNumberOfMasses(){
        return m_masses.size();
    }

    public int getNumberOfInteractions(){
        return m_interactions.size();
    }

    public ArrayList<Observer3D> getObservers(){
        ArrayList<Observer3D> list = new ArrayList<>();
        for(PhyModel pm : m_subModels)
            list.addAll(pm.getObservers());
        for(InOut element : m_inOuts){
            if(element.getType() == inOutType.OBSERVER3D) {
                Observer3D tmp = ((Observer3D)element);
                list.add(tmp);
            }
        }
        return list;
    }

    public ArrayList<Driver3D> getDrivers(){
        ArrayList<Driver3D> list = new ArrayList<>();
        for(PhyModel pm : m_subModels)
            list.addAll(pm.getDrivers());
        for(InOut element : m_inOuts){
            if(element.getType() == inOutType.DRIVER3D) {
                Driver3D tmp = ((Driver3D)element);
                list.add(tmp);
            }
        }
        return list;
    }



    public boolean massExists(String name) {
        Mass m = m_massLabels.get(name);
        if (m == null)
            return false;
        else
            return true;
    }


    private int removeMass(Mass m){
        try {
            if(m_massLabels.remove(m.getName()) == null)
                throw(new Exception("Couldn't remove Mass module " + m + "out of label list."));
            if(m_masses.remove(m) == false)
                throw(new Exception("Couldn't remove Mass module " + m + "out of Array list."));
            return 0;
        } catch (Exception e) {
            System.out.println("Error removing Mass Module " + m + ": " + e);
            return -1;
        }
    }

    public void setSimRate(int SR){
        m_simRate = SR;
    }


    private int removeMass(String name) {
        Mass m = m_massLabels.get(name);
        return removeMass(m);
    }


    public int removeInteraction(Interaction l) {
        synchronized (m_lock) {
            try {
                if(m_intLabels.remove(l.getName()) == null)
                    throw(new Exception("Couldn't remove Interaction module " + l.getName() + " out of label list."));
                if(m_interactions.remove(l)== false)
                    throw(new Exception("Couldn't remove Interaction module " + l.getName() + " out of Array list."));
                return 0;
            } catch (Exception e) {
                System.out.println("Error removing interaction Module " + l + ": " + e);
                return -1;
            }
        }
    }

    public synchronized int removeInteraction(String name) {
        Interaction l = m_intLabels.get(name);
        return removeInteraction(l);
    }


    public int removeMassAndConnectedInteractions(Mass m) {
        synchronized (m_lock) {
            try {
                for (int i = m_interactions.size() - 1; i >= 0; i--) {
                    Interaction cur = m_interactions.get(i);
                    if ((cur.getMat1() == m) || (cur.getMat2() == m))
                        if(removeInteraction(cur) != 0)
                            throw(new Exception("Couldn't remove Interaction module " + cur ));

                }
                if (removeMass(m) != 0)
                    throw(new Exception("Couldn't remove Mass module " + m));

                return 0;

            } catch (Exception e) {
                System.out.println("Issue removing connected interactions and mass!" + e);
                //System.exit(1);
            }
        }
        return -1;
    }

    public int removeMassAndConnectedInteractions(String mName) {
        Mass m = m_massLabels.get(mName);
        return removeMassAndConnectedInteractions(m);
    }

    // CHEAP HACK: have to implement these if we want them to be inherited from the Module class
    public int setParam(param p, double val ){
        System.out.println("This method is empty for a general physical model but can be overriden" +
                "for specific cases (e.g. strings, regular topologies, etc.");
        return -1;
    }
    public double getParam(param p){
        System.out.println("This method is empty for a general physical model but can be overriden" +
                "for specific cases (e.g. strings, regular topologies, etc.");
        return -1;
    }

    private void replaceMassInModel(Mass old, Mass m){
        int idx = m_masses.indexOf(old);
        m_masses.set(idx, m);
        m_massLabels.replace(m.getName(), m);
        for(Interaction i: m_interactions){
            if(i.getMat1() == old)
                i.connect(m, i.getMat2());
            if(i.getMat2() == old)
                i.connect(i.getMat1(), m);
        }
    }


    public void changeToFixedPoint(String masName){
        Mass m = m_massLabels.get(masName);
        this.changeToFixedPoint(m);
    }


    public void changeToFixedPoint(Mass m) {
        try {

            String name = m.getName();
            //System.out.println("Changing to fixed point:  " + m.getName());

            Ground3D tmp = new Ground3D(m.getParam(param.RADIUS), m.getPos());
            tmp.setName(name);

            replaceMassInModel(m, tmp);

            this.removeMassAndConnectedInteractions(m);

        } catch (Exception e) {
            System.out.println("Couldn't change into fixed point:  " + m.getName() + ": " + e);
            System.exit(1);
        }
    }


    public Mass getFirstMass(){
        return m_masses.get(0);
    }

    public Mass getLastMass(){
        return m_masses.get(m_masses.size()-1);
    }

    public void calcSpacePrint(){
        /*ForkJoinPool pool = null;
        pool = new ForkJoinPool(8);
        pool.submit(() -> m_masses.parallelStream().forEach(p-> m_sp.update(p)));
        */
        // Calculate the space print for this object.
        //m_masses.parallelStream().forEach(p-> m_sp.update(p));

        for(Mass m : m_masses){
            m_sp.update(m);
        }

        /*
        // Get the overall space print by adding prints from any submodules.
        m_sp_overall.set(m_sp);
        for(PhyModel pm : m_subModels) {
            //pm.calcSpacePrint();
            m_sp_overall.update(pm.m_sp);
        }
        */
    }

    public SpacePrint getSpacePrint(){
        return m_sp;
    }

    public void translate(float tx, float ty, float tz){
        translateAndRotate(tx, ty, tz, 0, 0, 0);
    }

    public void rotate(float x, float y, float z){
        translateAndRotate(0, 0, 0, x, y, z);
    }

    // Currently a problem with rotations along Y and Z, need to sort this...
    public void translateAndRotate(float tx, float ty, float tz, float x_angle, float y_angle, float z_angle){

        Vect3D mdlPos;

        Vect3D offset = new Vect3D();

        double cosa = Math.cos(x_angle);
        double sina = Math.sin(x_angle);

        double cosb = Math.cos(y_angle);
        double sinb = Math.sin(y_angle);

        double cosc = Math.cos(z_angle);
        double sinc = Math.sin(z_angle);

        double Axx = cosa*cosb;
        double Axy = cosa*sinb*sinc - sina*cosc;
        double Axz = cosa*sinb*cosc + sina*sinc;

        double Ayx = sina*cosb;
        double Ayy = sina*sinb*sinc + cosa*cosc;
        double Ayz = sina*sinb*cosc - cosa*sinc;

        double Azx = -sinb;
        double Azy = cosb*sinc;
        double Azz = cosb*cosc;

        offset.x = 0; // 0.5 * ((dimX-1) * dist);
        offset.y = 0; //0.5 * ((dimY-1) * dist);
        offset.z = 0; //0.5 * ((dimZ-1) * dist);

        Vect3D v = new Vect3D();


        for (int i = 0; i < this.getNumberOfMasses(); i++) {

            Mass tmp = this.getMassList().get(i);
            mdlPos = tmp.getPos();

            //System.out.println("Input pos: " + mdlPos + ", " + tmp.getName() + " " + tmp.getType());

            mdlPos.x -= offset.x;
            mdlPos.y -= offset.y;
            mdlPos.z -= offset.z;

            v.x = Axx*mdlPos.x + Axy*mdlPos.y + Axz*mdlPos.z;
            v.y = Ayx*mdlPos.x + Ayy*mdlPos.y + Ayz*mdlPos.z;
            v.z = Azx*mdlPos.x + Azy*mdlPos.y + Azz*mdlPos.z;

            //System.out.println("New Position: " + v);

            v.x += offset.x + tx;
            v.y += offset.y + ty;
            v.z += offset.z + tz;

            tmp.setPos(v);
        }
        for(PhyModel pm : getSubModels())
            pm.translateAndRotate(tx,ty,tz,x_angle, y_angle, z_angle);
    }



    /* Class attributes */
    private massType m_type;

    ArrayList<PhyModel> m_subModels = new ArrayList<>();
    ArrayList<Mass> m_masses = new ArrayList<>();
    ArrayList<InOut> m_inOuts = new ArrayList<>();
    ArrayList<Interaction> m_interactions = new ArrayList<>();

    HashMap<String, PhyModel> m_subModelLabels = new HashMap<>();
    HashMap<String, Mass> m_massLabels = new HashMap<>();
    HashMap<String, Interaction> m_intLabels = new HashMap<>();
    HashMap<String, InOut> m_inOutLabels = new HashMap<>();

    private velUnit m_velUnits = velUnit.PER_SEC;
    private int m_errorCode = 0;
    private int m_simRate = 1000;

    private SpacePrint m_sp = new SpacePrint();
    private SpacePrint m_sp_overall = new SpacePrint();

    private Lock m_lock;

    public Lock getLock(){
        return m_lock;
    }

}