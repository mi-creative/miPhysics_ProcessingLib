//One of many controls over a mass through midi NoteOn/NoteOff
//This one is really basic...
class midiNote
{

 float min;
 float max;
 String name;
  float a;
 float b;
 int indexMin;
 int indexMax;
 String direction;
  midiNote(float min_,float max_,String name_,int indexMin_,int indexMax_,String direction_)
 {

  min=min_;
  max=max_;
  name=name_;
  indexMin = indexMin_;
  indexMax = indexMax_;
  direction = direction_;
  computeScale();
 }
 void computeScale()
 {
      b = min;
   a = (max-min)/127;
 }
 void on(int pitch,int velocity)
 {
   if(pitch >= indexMin && pitch <= indexMax)
   {
   if( direction == "X") simUGen.mdl.triggerForceImpulse(name+pitch,a*velocity+b,0,0);
   if( direction == "Y") simUGen.mdl.triggerForceImpulse(name+pitch,0,a*velocity+b,0);
   if( direction == "Z") simUGen.mdl.triggerForceImpulse(name+pitch,0,0,a*velocity+b);
   }
 }
 void off(int pitch,int velocity)
 {
 }
}
