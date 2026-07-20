package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import qapSolver.Model.SampleQAPSolution;

/**
 * Plain main-class test harness for {@link QAPDataset} and
 * {@link SolutionRepository}: instance–solution matching over the whole
 * deposit (136 pairs, 128 with a solution, unmatched set = the known 8),
 * per-pair size agreement, single find/get behavior, and family access on
 * the solution side (26 tai solutions = 28 tai instances − tai10a/b).
 *
 * Usage: QAPDatasetTest [datDir] [slnDir]
 * (defaults: QAPData/qapdata, QAPData/qapsoln).
 * Exit code 0 = full pass, 1 = any failure.
 */
public final class QAPDatasetTest {

    private static final Set<String> EXPECTED_MISSING_SLN = Set.of(
            "esc32a", "esc32b", "esc32c", "esc32d", "esc32h", "esc64a", "tai10a", "tai10b");

    private QAPDatasetTest() {
    }

    public static void main(String[] args) throws IOException {
        QAPDataset dataset = new QAPDataset(
                Paths.get(args.length > 0 ? args[0] : "QAPData/qapdata"),
                Paths.get(args.length > 1 ? args[1] : "QAPData/qapsoln"));
        List<String> failures = new ArrayList<>();

        List<QAPDataset.Pair> pairs = dataset.pairs();
        check(failures, pairs.size() == 136, "pairs: expected 136, got " + pairs.size());

        int withSolution = 0;
        Set<String> missing = new TreeSet<>();
        for (QAPDataset.Pair pair : pairs) {
            Optional<SampleQAPSolution> sol = pair.getSolution();
            if (!sol.isPresent()) {
                missing.add(pair.getInstance().getName());
                continue;
            }
            withSolution++;
            if (pair.getInstance().getSize() != sol.get().getSize()) {
                failures.add(pair.getInstance().getName() + ": instance n=" + pair.getInstance().getSize()
                        + " but solution n=" + sol.get().getSize());
            }
            if (!pair.getInstance().getName().equals(sol.get().getInstanceName())) {
                failures.add(pair.getInstance().getName() + ": paired with solution named "
                        + sol.get().getInstanceName());
            }
        }
        check(failures, withSolution == 128, "pairs: expected 128 with solution, got " + withSolution);
        check(failures, missing.equals(EXPECTED_MISSING_SLN),
                "unmatched instances " + missing + " != expected " + EXPECTED_MISSING_SLN);

        Optional<SampleQAPSolution> tai60a = dataset.findSolution("tai60a");
        check(failures, tai60a.isPresent() && tai60a.get().getSize() == 60,
                "findSolution(tai60a): expected present with n=60");
        check(failures, !dataset.findSolution("esc32a").isPresent(),
                "findSolution(esc32a): expected empty");
        check(failures, dataset.getInstance("esc32a").getSize() == 32,
                "getInstance(esc32a): expected n=32");

        SolutionRepository solutions = dataset.getSolutionRepository();
        check(failures, solutions.listNames().size() == 128,
                "solution listNames: expected 128, got " + solutions.listNames().size());
        check(failures, solutions.getFamily("tai").size() == 26,
                "solution getFamily(tai): expected 26, got " + solutions.getFamily("tai").size());

        boolean threw = false;
        try {
            solutions.get("doesnotexist");
        } catch (IOException e) {
            threw = true;
        }
        check(failures, threw, "solutions.get(doesnotexist): expected IOException");

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
