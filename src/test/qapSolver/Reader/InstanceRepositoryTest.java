package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plain main-class test harness for {@link InstanceRepository}: single get,
 * family get (counts checked against known dataset facts: 28 tai, 13 sko),
 * getAll (136), listNames consistency, exact-prefix family matching, and
 * error behavior on unknown names.
 *
 * Usage: InstanceRepositoryTest [datDir]  (default: QAPData/qapdata).
 * Exit code 0 = full pass, 1 = any failure.
 */
public final class InstanceRepositoryTest {

    private InstanceRepositoryTest() {
    }

    public static void main(String[] args) throws IOException {
        InstanceRepository repo = new InstanceRepository(
                Paths.get(args.length > 0 ? args[0] : "QAPData/qapdata"));
        List<String> failures = new ArrayList<>();

        List<String> names = repo.listNames();
        check(failures, names.size() == 136, "listNames: expected 136 names, got " + names.size());
        check(failures, names.contains("tai60a"), "listNames: tai60a missing");
        List<String> sorted = names.stream().sorted().collect(Collectors.toList());
        check(failures, names.equals(sorted), "listNames: not sorted");

        QapInstance one = repo.get("tai60a");
        check(failures, one.getSize() == 60, "get(tai60a): expected n=60, got " + one.getSize());
        check(failures, one.getName().equals("tai60a"), "get(tai60a): wrong name " + one.getName());

        boolean threw = false;
        try {
            repo.get("doesnotexist");
        } catch (IOException e) {
            threw = true;
        }
        check(failures, threw, "get(doesnotexist): expected IOException");

        List<QapInstance> tai = repo.getFamily("tai");
        check(failures, tai.size() == 28, "getFamily(tai): expected 28, got " + tai.size());
        check(failures, tai.stream().allMatch(i -> i.getName().startsWith("tai")),
                "getFamily(tai): non-tai instance in result");

        List<QapInstance> sko = repo.getFamily("sko");
        check(failures, sko.size() == 13, "getFamily(sko): expected 13, got " + sko.size());

        check(failures, repo.getFamily("t").isEmpty(), "getFamily(t): exact match violated");
        check(failures, repo.getFamily("unknown").isEmpty(), "getFamily(unknown): expected empty");

        List<QapInstance> all = repo.getAll();
        check(failures, all.size() == 136, "getAll: expected 136, got " + all.size());
        List<String> allNames = all.stream().map(QapInstance::getName).collect(Collectors.toList());
        check(failures, allNames.equals(names), "getAll: names differ from listNames");

        check(failures, InstanceRepository.familyOf("tai100a").equals("tai"), "familyOf(tai100a) != tai");
        check(failures, InstanceRepository.familyOf("esc16f").equals("esc"), "familyOf(esc16f) != esc");

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
