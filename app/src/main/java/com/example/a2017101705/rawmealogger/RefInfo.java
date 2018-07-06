package com.example.a2017101705.rawmealogger;

import java.io.Serializable;

/**
 * Created by HP on 2018/6/3.
 */

public class   RefInfo implements Serializable {
        private int Svid;
        private int SOW;
        private double refPr;
        private double refPhase;

        public void setSvid(int Svid){
            this.Svid  = Svid;
        }
        public void setSOW(int SOW){
            this.SOW = SOW;
        }
        public void setRefPr(double refPr){
            this.refPr = refPr;
        }
        public void setRefPhase(double refPhase){
            this.refPhase = refPhase;
        }
        public  int getSvid(){
            return  Svid;
        }
        public int getSOW(){
            return  SOW;
        }
        public double getRefPr(){
            return  refPr;
        }
        public  double getRefPhase(){
            return  refPhase;
        }

    }
