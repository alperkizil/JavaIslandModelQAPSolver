package qapSolver.Objective;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;
import qapSolver.Reader.QAPDataset;

/**
 * Plain main-class test harness proving ObjectiveFunction + SolutionVerifier
 * correct against every sample solution in the deposit:
 *
 * - 119 solutions (incl. degenerate esc16f) must verify as-is;
 * - the 8 inverse-convention .sln files must fail verify() but reproduce
 *   their value when the inverted permutation is evaluated;
 * - kra32 must fail verify() (header typo 88900) and evaluate to the true
 *   optimum 88700 (as-is or inverted);
 * - a negative control (tampered claimed value) must fail verify().
 *
 * Usage: SolutionVerifierTest [datDir] [slnDir]
 * (defaults: QAPData/qapdata, QAPData/qapsoln).
 * Exit code 0 = full pass, 1 = any failure.
 */
public final class SolutionVerifierTest {

    private static final Set<String> INVERSE_CONVENTION = Set.of(
            "esc128", "kra30a", "kra30b", "ste36c", "tai60a", "tai80a", "tho30", "tho150");

    private static final long KRA32_TRUE_VALUE = 88700L;

    private SolutionVerifierTest() {
    }

    public static void main(String[] args) throws IOException {
        QAPDataset dataset = new QAPDataset(
                Paths.get(args.length > 0 ? args[0] : "QAPData/qapdata"),
                Paths.get(args.length > 1 ? args[1] : "QAPData/qapsoln"));
        List<String> failures = new ArrayList<>();

        int verified = 0;
        int inverseConfirmed = 0;
        boolean kra32Confirmed = false;

        for (QAPDataset.Pair pair : dataset.pairs()) {
            if (!pair.getSolution().isPresent()) {
                continue;
            }
            QAPInstance inst = pair.getInstance();
            SampleQAPSolution sol = pair.getSolution().get();
            String name = sol.getName();

            if (name.equals("kra32")) {
                if (SolutionVerifier.verify(inst, sol)) {
                    failures.add("kra32: verify() true against the typo header value 88900");
                }
                long asIs = ObjectiveFunction.evaluate(inst, sol);
                long inverted = ObjectiveFunction.evaluate(inst, invert(sol.getPermutation()));
                if (asIs == KRA32_TRUE_VALUE || inverted == KRA32_TRUE_VALUE) {
                    kra32Confirmed = true;
                } else {
                    failures.add("kra32: expected " + KRA32_TRUE_VALUE + ", got as-is=" + asIs
                            + " inverted=" + inverted);
                }
            } else if (INVERSE_CONVENTION.contains(name)) {
                if (SolutionVerifier.verify(inst, sol)) {
                    failures.add(name + ": verify() true although file is inverse-convention");
                }
                long inverted = ObjectiveFunction.evaluate(inst, invert(sol.getPermutation()));
                if (inverted == sol.getValue()) {
                    inverseConfirmed++;
                } else {
                    failures.add(name + ": inverted permutation gives " + inverted
                            + ", claimed " + sol.getValue());
                }
            } else {
                if (SolutionVerifier.verify(inst, sol)) {
                    verified++;
                } else {
                    failures.add(name + ": verify() false, claimed " + sol.getValue()
                            + ", computed " + ObjectiveFunction.evaluate(inst, sol));
                }
            }
        }

        if (verified != 119) {
            failures.add("expected 119 directly verified solutions, got " + verified);
        }
        if (inverseConfirmed != 8) {
            failures.add("expected 8 inverse-convention confirmations, got " + inverseConfirmed);
        }
        if (!kra32Confirmed) {
            failures.add("kra32 true value " + KRA32_TRUE_VALUE + " not confirmed");
        }

        QAPInstance tai12a = dataset.getInstance("tai12a");
        SampleQAPSolution good = dataset.findSolution("tai12a").get();
        SampleQAPSolution tampered = new SampleQAPSolution(
                good.getName(), good.getValue() + 1, good.getPermutation());
        if (SolutionVerifier.verify(tai12a, tampered)) {
            failures.add("negative control: tampered tai12a value passed verify()");
        }

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println();
        System.out.println("verified as-is        : " + verified + "/119");
        System.out.println("inverse-convention OK : " + inverseConfirmed + "/8 (via inverted permutation)");
        System.out.println("kra32 -> " + KRA32_TRUE_VALUE + "        : " + (kra32Confirmed ? "confirmed" : "NOT confirmed"));
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    /** q = p⁻¹, i.e. q[p[i]] = i. */
    private static int[] invert(int[] p) {
        int[] inv = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            inv[p[i]] = i;
        }
        return inv;
    }
}
