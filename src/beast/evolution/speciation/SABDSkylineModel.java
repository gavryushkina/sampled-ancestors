package beast.evolution.speciation;

import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import beast.evolution.tree.ZeroBranchSANode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexandra Gavryushkina
 */
public class SABDSkylineModel extends BirthDeathSkylineModel {

    public Input<RealParameter> becomeNoninfectiousAfterSamplingProbability =
            new Input<RealParameter>("becomeNoninfectiousAfterSamplingProbability", "The probability of an individual to become noninfectious immediately after the sampling", Input.Validate.REQUIRED);

    protected double r;

    @Override
    public void initAndValidate() throws Exception {
        super.initAndValidate();
        r = becomeNoninfectiousAfterSamplingProbability.get().getValue();
        //printTempResults = true;
    }

    @Override
    protected void transformParameters() {

        Double[] R = R0.get().getValues();
        Double[] b = becomeUninfectiousRate.get().getValues();
        Double[] p = samplingProportion.get().getValues();
        r = becomeNoninfectiousAfterSamplingProbability.get().getValue();

        birth = new Double[totalIntervals];
        death = new Double[totalIntervals];
        psi = new Double[totalIntervals];


        if (isBDSIR()) birth[0] = R[0] * b[0]; // the rest will be done in BDSIR class

        for (int i = 0; i < totalIntervals; i++) {
            if (!isBDSIR()) birth[i] = R[birthChanges>0 ? index(times[i], birthRateChangeTimes) : 0] * b[deathChanges>0 ? index(times[i], deathRateChangeTimes) : 0];
            psi[i] = p[samplingChanges>0 ? index(times[i], samplingRateChangeTimes) : 0] * b[deathChanges>0 ? index(times[i], deathRateChangeTimes) : 0]/r;
            death[i] = b[deathChanges>0 ? index(times[i], deathRateChangeTimes) : 0] - r*psi[i];

        }
    }

    @Override
    public double calculateTreeLogLikelihood(TreeInterface tree) {

        int nTips = tree.getLeafNodeCount();

        if (preCalculation((Tree)tree) < 0)
            return Double.NEGATIVE_INFINITY;

        r = becomeNoninfectiousAfterSamplingProbability.get().getValue();

        // number of lineages at each time ti
        int[] n = new int[totalIntervals];

        double x0 = 0;
        int index = 0;

        double temp;

        // the first factor for origin
        if (!conditionOnSurvival.get())
            temp = Math.log(g(index, times[index], x0));  // NOT conditioned on at least one sampled individual
        else {
            temp = p0(index, times[index], x0);
            if (temp == 1)
                return Double.NEGATIVE_INFINITY;
            temp = Math.log(g(index, times[index], x0) / (1 - temp));   // DEFAULT: conditioned on at least one sampled individual
        }

        logP = temp;
        if (Double.isInfinite(logP))
            return logP;

        if (printTempResults) System.out.println("first factor for origin = " + temp);

        // first product term in f[T]
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            double x = times[totalIntervals - 1] - tree.getNode(nTips + i).getHeight();
            index = index(x);
            if (!((ZeroBranchSANode)tree.getNode(nTips + i)).isFake()) {
                temp = Math.log(birth[index] * g(index, times[index], x));
                logP += temp;
                if (printTempResults) System.out.println("1st pwd" +
                        " = " + temp + "; interval = " + i);
                if (Double.isInfinite(logP)) {
                    return logP;
                }
            } else {
                if (r != 1) {
                    logP += Math.log((1 - r)*psi[index]);
                    if (Double.isInfinite(logP)) {
                        return logP;
                    }
                    //System.out.println("caught it. The time of sampled ancestor is " + tree.getNode(nTips+i).getHeight());
                } else {
                    //throw new Exception("There is a sampled ancestor in the tree while r parameter is 1");
                    System.out.println("There is a sampled ancestor in the tree while r parameter is 1");
                    System.exit(0);
                }
            }
        }

        // middle product term in f[T]
        for (int i = 0; i < nTips; i++) {

            if ((!isRhoTip[i] || m_rho.get() == null) && !((ZeroBranchSANode)tree.getNode(i)).isDirectAncestor()) {
                double y = times[totalIntervals - 1] - tree.getNode(i).getHeight();
                index = index(y);
                temp = Math.log(psi[index] * (r + (1-r)*p0[index])) - Math.log(g(index, times[index], y));
                logP += temp;
                if (printTempResults) System.out.println("2nd PI = " + temp);
                if (Double.isInfinite(logP)){
                    return logP;
                }
            }
        }

        // last product term in f[T], factorizing from 1 to m
        double time;
        for (int j = 0; j < totalIntervals; j++) {
            time = j < 1 ? 0 : times[j - 1];
            n[j] = ((j == 0) ? 0 : lineageCountAtTime(times[totalIntervals - 1] - time, (Tree)tree));

            if (n[j] > 0) {
                temp = n[j] * (Math.log(g(j, times[j], time)) + Math.log(1-rho[j]));
                logP += temp;
                if (printTempResults)
                    System.out.println("3rd factor (nj loop) = " + temp + "; interval = " + j + "; n[j] = " + n[j]);//+ "; Math.log(g(j, times[j], time)) = " + Math.log(g(j, times[j], time)));
                if (Double.isInfinite(logP)) {
                    System.out.println("forth");
                    System.exit(0);
                    return logP;
                }
            }
            if (rho[j] > 0 && N[j] > 0) {
                temp = N[j] * Math.log(rho[j]);    // term for contemporaneous sampling
                logP += temp;
                if (printTempResults)
                    System.out.println("3rd factor (Nj loop) = " + temp + "; interval = " + j + "; N[j] = " + N[j]);
                if (Double.isInfinite(logP)){
                    System.out.println("fifth");
                    System.exit(0);
                    return logP;
                }
            }
        }
        return logP;
    }
    
    
}