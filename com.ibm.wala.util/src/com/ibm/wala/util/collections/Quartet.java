package com.ibm.wala.util.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.ibm.wala.util.debug.Assertions;

public class Quartet<T,U,P,Q> {

  public final T fst;
  public final U snd;
  public final P thr;
  public final Q frt;

  protected Quartet(T fst, U snd, P thr, Q frt) {
    this.fst = fst;
    this.snd = snd;
    this.thr = thr;
    this.frt = frt;
  }

  private boolean check(Object x, Object y) {
    return (x == null) ? (y == null) : x.equals(y);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public boolean equals(Object o) {
    return ((o instanceof Quartet) 
        && check(fst, ((Quartet) o).fst) 
        && check(snd, ((Quartet) o).snd) 
        && check(thr, ((Quartet) o).thr)
        && check(frt, ((Quartet) o).frt));
  }

  private int hc(Object o) {
    return (o == null) ? 0 : o.hashCode();
  }

  @Override
  public int hashCode() {
    return hc(fst) * 7219 +  13 * hc(snd) + 
        123 * hc(thr) + 8213 * hc(frt);
  }

  public Iterator<Object> iterator() {
    return new Iterator<Object>() {
      byte next = 1;

      public boolean hasNext() {
        return next > 0;
      }

      public Object next() {
        switch (next) {
          case 1 :
            next++;
            return fst;
          case 2 :
            next++;
            return snd;
          case 3 :
            next ++;
            return thr;
          case 4 :
            next = 0;
            return frt;
          default :
            throw new NoSuchElementException();
        }
      }

      public void remove() {
        Assertions.UNREACHABLE();
      }
    };
  }
  
  @Override
  public String toString() {
    return "[" + fst + "," + snd + "," + thr + "," + frt + "]";
  }

  public static <T,U,P,Q> Quartet<T,U,P,Q> make(T w, U x, P y, Q z) {
    return new Quartet<T,U,P,Q>(w,x,y,z);
  }

}
