package qapSolver.Objective;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import qapSolver.Model.CustomSolution;
import qapSolver.Model.Permutations;
import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;
import qapSolver.Reader.QAPDataset;

/**
 * Plain main-class test harness proving ObjectiveFunction + SolutionVerifier
 * correct against every sample solution in the deposit:
 *
 * - 127 solutions (incl. degenerate esc16f and the 8 inverse-convention
 *   files, which the reader auto-normalizes to the standard orientation)
 *   must verify as-is and carry isValid() = true;
 * - kra32 must fail verify() (header typo 88900) and evaluate to the true
 *   optimum 88700 (as-is or inverted);
 * - isValid() must agree with verify() everywhere;
 * - negative controls (tampered claimed values) must fail.
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
        int inverseNormalized = 0;
        boolean kra32Confirmed = false;

        for (QAPDataset.Pair pair : dataset.pairs()) {
            if (!pair.getSolution().isPresent()) {
                continue;
            }
            QAPInstance inst = pair.getInstance();
            SampleQAPSolution sol = pair.getSolution().get();
            String name = sol.getInstanceName();

            if (SolutionVerifier.verify(inst, sol) != sol.isValid()) {
                failures.add(name + ": isValid() inconsistent with verify()");
            }

            if (name.equals("kra32")) {
                if (SolutionVerifier.verify(inst, sol)) {
                    failures.add("kra32: verify() true against the typo header value 88900");
                }
                long asIs = ObjectiveFunction.evaluate(inst, sol);
                long inverted = ObjectiveFunction.evaluate(inst,
                        Permutations.inverseOf(sol.getPermutation()));
                if (asIs == KRA32_TRUE_VALUE || inverted == KRA32_TRUE_VALUE) {
                    kra32Confirmed = true;
                } else {
                    failures.add("kra32: expected " + KRA32_TRUE_VALUE + ", got as-is=" + asIs
                            + " inverted=" + inverted);
                }
            } else if (sol.isValid()) {
                verified++;
                if (INVERSE_CONVENTION.contains(name)) {
                    inverseNormalized++;
                }
            } else {
                failures.add(name + ": isValid() false, claimed " + sol.getValue()
                        + ", computed " + ObjectiveFunction.evaluate(inst, sol)
                        + " (inverted: " + ObjectiveFunction.evaluate(inst,
                                Permutations.inverseOf(sol.getPermutation())) + ")");
            }
        }

        if (verified != 127) {
            failures.add("expected 127 valid solutions, got " + verified);
        }
        if (inverseNormalized != 8) {
            failures.add("expected the 8 inverse-convention files normalized and valid, got "
                    + inverseNormalized);
        }
        if (!kra32Confirmed) {
            failures.add("kra32 true value " + KRA32_TRUE_VALUE + " not confirmed");
        }

        QAPInstance tai12a = dataset.getInstance("tai12a");
        SampleQAPSolution good = dataset.findSolution("tai12a").get();
        SampleQAPSolution tampered = new SampleQAPSolution(
                tai12a, good.getValue() + 1, good.getPermutation());
        if (SolutionVerifier.verify(tai12a, tampered) || tampered.isValid()) {
            failures.add("negative control: tampered tai12a value passed verify()/isValid()");
        }

        int[] identity = new int[tai12a.getSize()];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }
        CustomSolution custom = new CustomSolution(
                tai12a, ObjectiveFunction.evaluate(tai12a, identity), identity);
        if (!custom.isValid() || !custom.getInstanceName().equals("tai12a")
                || custom.getSize() != 12) {
            failures.add("CustomSolution: expected valid identity solution for tai12a, got " + custom);
        }
        CustomSolution wrongClaim = new CustomSolution(
                tai12a, custom.getValue() + 1, identity.clone());
        if (wrongClaim.isValid()) {
            failures.add("CustomSolution: wrong claimed value must yield isValid()=false");
        }

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println();
        System.out.println("verified & valid       : " + verified + "/127");
        System.out.println("of which normalized    : " + inverseNormalized + "/8 (inverse-convention files)");
        System.out.println("kra32 -> " + KRA32_TRUE_VALUE + "         : " + (kra32Confirmed ? "confirmed" : "NOT confirmed"));
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

}
