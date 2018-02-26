package ps.uiet.chd.sensortasks;

public class SVC {

    private enum Kernel { LINEAR, POLY, RBF, SIGMOID }

    private int nClasses;
    private int nRows;
    private int[] classes;
    private double[][] vectors;
    private double[][] coefficients;
    private double[] intercepts;
    private int[] weights;
    private Kernel kernel;
    private double gamma;
    private double coef0;
    private double degree;

    public SVC (int nClasses, int nRows, double[][] vectors, double[][] coefficients, double[] intercepts, int[] weights, String kernel, double gamma, double coef0, double degree) {
        this.nClasses = nClasses;
        this.classes = new int[nClasses];
        for (int i = 0; i < nClasses; i++) {
            this.classes[i] = i;
        }
        this.nRows = nRows;

        this.vectors = vectors;
        this.coefficients = coefficients;
        this.intercepts = intercepts;
        this.weights = weights;

        this.kernel = Kernel.valueOf(kernel.toUpperCase());
        this.gamma = gamma;
        this.coef0 = coef0;
        this.degree = degree;
    }

    public int predict(double[] features) {

        double[] kernels = new double[vectors.length];
        double kernel;
        switch (this.kernel) {
            case LINEAR:
                // <x,x'>
                for (int i = 0; i < this.vectors.length; i++) {
                    kernel = 0.;
                    for (int j = 0; j < this.vectors[i].length; j++) {
                        kernel += this.vectors[i][j] * features[j];
                    }
                    kernels[i] = kernel;
                }
                break;
            case POLY:
                // (y<x,x'>+r)^d
                for (int i = 0; i < this.vectors.length; i++) {
                    kernel = 0.;
                    for (int j = 0; j < this.vectors[i].length; j++) {
                        kernel += this.vectors[i][j] * features[j];
                    }
                    kernels[i] = Math.pow((this.gamma * kernel) + this.coef0, this.degree);
                }
                break;
            case RBF:
                // exp(-y|x-x'|^2)
                for (int i = 0; i < this.vectors.length; i++) {
                    kernel = 0.;
                    for (int j = 0; j < this.vectors[i].length; j++) {
                        kernel += Math.pow(this.vectors[i][j] - features[j], 2);
                    }
                    kernels[i] = Math.exp(-this.gamma * kernel);
                }
                break;
            case SIGMOID:
                // tanh(y<x,x'>+r)
                for (int i = 0; i < this.vectors.length; i++) {
                    kernel = 0.;
                    for (int j = 0; j < this.vectors[i].length; j++) {
                        kernel += this.vectors[i][j] * features[j];
                    }
                    kernels[i] = Math.tanh((this.gamma * kernel) + this.coef0);
                }
                break;
        }

        int[] starts = new int[this.nRows];
        for (int i = 0; i < this.nRows; i++) {
            if (i != 0) {
                int start = 0;
                for (int j = 0; j < i; j++) {
                    start += this.weights[j];
                }
                starts[i] = start;
            } else {
                starts[0] = 0;
            }
        }

        int[] ends = new int[this.nRows];
        for (int i = 0; i < this.nRows; i++) {
            ends[i] = this.weights[i] + starts[i];
        }

        if (this.nClasses == 2) {

            for (int i = 0; i < kernels.length; i++) {
                kernels[i] = -kernels[i];
            }

            double decision = 0.;
            for (int k = starts[1]; k < ends[1]; k++) {
                decision += kernels[k] * this.coefficients[0][k];
            }
            for (int k = starts[0]; k < ends[0]; k++) {
                decision += kernels[k] * this.coefficients[0][k];
            }
            decision += this.intercepts[0];

            if (decision > 0) {
                return 0;
            }
            return 1;

        }

        double[] decisions = new double[this.intercepts.length];
        for (int i = 0, d = 0, l = this.nRows; i < l; i++) {
            for (int j = i + 1; j < l; j++) {
                double tmp = 0.;
                for (int k = starts[j]; k < ends[j]; k++) {
                    tmp += this.coefficients[i][k] * kernels[k];
                }
                for (int k = starts[i]; k < ends[i]; k++) {
                    tmp += this.coefficients[j - 1][k] * kernels[k];
                }
                decisions[d] = tmp + this.intercepts[d];
                d++;
            }
        }

        int[] votes = new int[this.intercepts.length];
        for (int i = 0, d = 0, l = this.nRows; i < l; i++) {
            for (int j = i + 1; j < l; j++) {
                votes[d] = decisions[d] > 0 ? i : j;
                d++;
            }
        }

        int[] amounts = new int[this.nClasses];
        for (int vote : votes) {
            amounts[vote] += 1;
        }

        int classVal = -1, classIdx = -1;
        for (int i = 0, l = amounts.length; i < l; i++) {
            if (amounts[i] > classVal) {
                classVal = amounts[i];
                classIdx= i;
            }
        }
        return this.classes[classIdx];

    }

    public static int main(String[] args, double[][] vectorsData) {
        if (args.length == 4)
        {

            // Features:
            double[] features = new double[args.length];
            for (int i = 0, l = args.length; i < l; i++)
            {
                features[i] = Double.parseDouble(args[i]);
            }

            // Parameters:
            double[][] vectors = vectorsData;
            double[][] coefficients = {{0.3959550931467534, 0.23089883350097853, 0.37933634400429483, 0.0, 0.0, 0.1207277729641782, 0.1621239915048873, 0.41590252535013483, 0.42273531477354537, 0.2220189421725938, 0.17453280381926056, 0.19777532792955155, 0.0, 0.051083559723006336, 100.0, 0.0, 0.489316342913068, 0.3633081650371461, 0.0, 1.9208546614170499, 0.0, 0.062025091175520554, 0.0, 0.07919346866998009, 0.0, 3.0252889434371935, 0.2498277295592598, 19.195698390474966, 0.4320239786804275, 0.49526710031291893, 0.10118163036192228, 0.4461116338637682, 0.286589374830126, 0.15496593013483606, 0.1020242704017058, 0.3756230328607056, 0.0, -0.0, -6.089094934045689, -19.199648053179555, -0.0, -13.755846513043869, -42.43345935516169, -2.154504110748017, -0.0, -46.33173710897293, -0.1745671489148733, -0.0, -0.0, -0.41353302895313715, -0.0, -0.7598866328707381, -0.20238309869926283, -0.6002849896789851, -0.48562767556675446, -0.43088368418668416, -0.5535920651982199, -0.6852996211917395, -0.3123300795596908, -0.8742810331319129, -0.21290604175047423, -0.035252291106077575, -0.273290160840213, -0.2584070141381876, -0.8670303142801308, -0.2172966876006088, -0.6180455856731474, -0.11647940783418045, -0.6748752255591972, -0.1270093053748393, -0.49042486449049993, -0.3483085409586951, -0.7762387820018384, -0.10431682875322562, -0.9772326748383611, -0.8252189870448798, -1.059037977209921, -0.9989120257507541, -0.6345035445879181, -0.2029465125177954}, {0.6530130759873342, 0.31210533529300005, 0.6266028262903182, 0.1127141169290892, 0.2685858559108308, 0.3352305508184997, 0.08581905314343778, 0.6060185995852579, 0.6796806508413421, 0.3206259979400852, 0.4596716851076234, 0.3398795930983461, 0.26280980035512136, 0.5878889940790845, 0.632045036071305, 0.2885964362138338, 0.7726177254114445, 0.7829343083434768, 0.026959834875296372, 0.6055426023670026, 0.18615218598597164, 0.0, 0.015475577859262459, 0.42532286335535163, 0.499308955914874, 0.0, 0.46026373796243175, 0.22382218598467332, 0.7030924608050488, 0.8170272128166334, 0.0, 0.7335614508037259, 0.4643447509079356, 0.2638739685106292, 0.0, 0.6138228746614846, 0.5568913481651808, 0.550939108950973, 0.620330060164427, 0.0, 0.9510387011533175, 0.0, 0.8923094137113515, 0.23084880876616842, 0.878095789304478, 0.14402494333270344, 0.0, 0.32149740917149056, 0.1218491448129882, 0.7354964017327112, 0.4251419613053393, -0.2997923759191083, -0.08915076749792314, -0.2391650809994045, -0.19375117913404563, -0.17225109630144655, -0.19740741077552462, -0.27393964929027537, -0.1270741771245747, -0.34994716104249673, -0.08665869041215361, -0.011915500530471327, -0.1091790696394986, -0.10210312660680967, -0.34566221606378833, -0.08645918948781676, -0.24792302944672331, -0.061018136352421495, -0.26860180374304976, -0.04993802336210778, -0.19623226941445804, -0.13989730413950913, -0.3104521001314823, -0.038967886012567385, -0.3906072718336292, -0.3304722143381971, -0.422790645833452, -0.39889829133584875, -0.2541852761284905, -0.07713079950867346}};
            double[] intercepts = {0.4268549813636277, 0.065455105974825, -0.5744741771456765};
            int[] weights = {37, 14, 29};

            // Prediction:
            SVC clf = new SVC(3, 3, vectors, coefficients, intercepts, weights, "rbf", 0.1, 0.0, 3);
            return clf.predict(features);

        }
        return -1;
    }
}