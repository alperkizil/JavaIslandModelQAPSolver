# HANDOFF — implementation state

Session handoff for **JavaIslandModelQAPSolver** (July 2026). Working rules and
dataset background live in [CLAUDE.md](CLAUDE.md); this file records what has
been built so far, the decisions behind it, and how to run it.

## Status

Foundation layer complete: dataset model, readers/repositories, objective
function, solution verification. All 136 QAPLIB instances read correctly; all
128 sample solutions load, normalize, and verify clean. First solver
infrastructure piece added: `qapSolver.Random` — seeded, thread-reproducible
randomness. No GA/SA solver code yet — that is the next phase.

## Layout & toolchain

- **JDK 11+** (`javac --release 11` enforced), no build tool, no dependencies.
- Production code under `src/main/`, test harnesses under `src/test/`
  (plain main-class runners, exit code 0 = PASS, 1 = failure).
- Packages are capitalized by project convention: `qapSolver.Model`,
  `qapSolver.Reader`, `qapSolver.Objective`.

Build & run everything:

```
javac --release 11 -d out/main $(find src/main -name '*.java')
javac --release 11 -cp out/main -d out/test $(find src/test -name '*.java')
java -cp out/main:out/test qapSolver.Reader.InstanceReaderTest
java -cp out/main:out/test qapSolver.Reader.InstanceRepositoryTest
java -cp out/main:out/test qapSolver.Reader.QAPDatasetTest
java -cp out/main:out/test qapSolver.Objective.SolutionVerifierTest
java -cp out/main:out/test qapSolver.Random.RandomizerTest
```

Test harnesses default to `QAPData/qapdata` / `QAPData/qapsoln` relative to the
working directory (repo root); both can be overridden via args.

## Architecture

### `qapSolver.Model` — domain types

| Class | Responsibility |
|---|---|
| `QAPInstance` | Immutable instance: name, size n, matrices A and B as read from the `.dat` (A first). No symmetry/zero-diagonal assumptions. Getters expose internal arrays by design (hot loops) — do not mutate. |
| `QAPSolution` *(abstract)* | Full common solution state: `instanceName`, objective value, 0-based permutation, size, and `isValid()`. Constructor takes the `QAPInstance`, validates the permutation (bijection, length = n) and **auto-verifies** via `SolutionVerifier` — valid ⇔ claimed value reproduced by the objective. Subclasses only express provenance. Future solver individuals extend this same base. |
| `SampleQAPSolution` | A `.sln` reference solution. After reader normalization all 128 are `isValid()=true`. |
| `CustomSolution` | Hand-crafted / solver-produced solution; same constructor shape `(instance, value, permutation)`. |
| `Permutations` | `inverseOf(p)`: converts between the two QAPLIB permutation conventions. Needed again for the future `.sln` writer. |

### `qapSolver.Reader` — parsing and access

| Class | Responsibility |
|---|---|
| `InstanceReader` | Strict `.dat` parser: exactly `1 + 2n²` whitespace/comma-separated integer tokens, loud failure on drift. |
| `SolutionReader` | Strict `.sln` parser (`2 + n` tokens) requiring the matching `QAPInstance` (name and n cross-checked). Applies three normalizations (below). |
| `InstanceRepository` | Name-based access to the `.dat` directory: `get(name)`, `getFamily(prefix)` (exact family match, `familyOf` = alphabetic prefix), `getAll()`, `listNames()`. No caching; sorted, deterministic. |
| `SolutionRepository` | Mirror over `.sln`; needs an `InstanceRepository` (construction verifies against the instance). Adds `find(name)` → `Optional` (8 instances have no `.sln`). |
| `QAPDataset` | Facade pairing both sides by name: `getInstance`, `findSolution`, `pairs()` (all 136, solution where present), plus repository accessors. |
| `RepositoryFiles` | Package-private shared directory listing. |

### `qapSolver.Objective` — evaluation

| Class | Responsibility |
|---|---|
| `ObjectiveFunction` | `cost(p) = Σᵢ Σⱼ A[i][j]·B[p(i)][p(j)]` — full O(n²), no symmetry assumed, diagonals included, `long` accumulation, primitive loops. Overloads: raw `int[]` (hot path) and `QAPSolution`. |
| `SolutionVerifier` | `verify(instance, solution)`: recomputes and compares against the claimed value. Mechanical; quirk-free. |

### `qapSolver.Random` — deterministic randomness

| Class | Responsibility |
|---|---|
| `RandomSource` | The single source of randomness for a run. Immutable (thread-safe) holder of the master seed; `derive(streamId)` returns the stream for that id — a pure function of `(masterSeed, streamId)`, independent of derivation order, thread count, and scheduling. No-arg constructor picks a per-JVM unique seed; `getMasterSeed()` exposes it for logging/replay. Derivation mirrors `SplittableRandom.split()` keyed by index: state = `mix64(masterSeed + (2i+1)·G)`, gamma = `mixGamma(masterSeed + (2i+2)·G)`. |
| `Randomizer` | One derived stream; **deliberately not thread-safe** — each instance is confined to one thread (one island). API: `nextLong()`, `nextInt(bound)` (exact-uniform, power-of-two fast path + tail rejection), `nextDouble()` (53-bit, [0,1)), `shuffle(int[])` (in-place Fisher–Yates). |
| `SplitMix64` | Package-private pure math: `GOLDEN_GAMMA`, `mix64` (Stafford variant 13), `mixGamma` (MurmurHash3 finalizer, forced odd, ≥24 bit transitions). Hand-rolled per the OOPSLA 2014 paper / Vigna's splitmix64.c so sequences are bit-identical on every JDK. |

## `.sln` normalizations (SolutionReader)

Every solution in memory obeys one set of conventions; files are normalized on
the way in:

1. **Indexing** — 1-based files shifted to 0-based; already-0-based (tai40a)
   kept; anything else rejected.
2. **kra32 header correction** — header 88900 is a known QAPLIB typo (belongs
   to kra30a); corrected to the true optimum **88700** only when the exact
   documented situation is present (name kra32 + header 88900 + permutation
   evaluating to 88700). Never a blanket trust-the-permutation rule.
3. **Orientation** — value-driven, no hardcoded name list: if the claimed value
   reproduces only on the inverted permutation, the inversion is stored. This
   normalizes the eight inverse-convention files (esc128, kra30a, kra30b,
   ste36c, tai60a, tai80a, tho30, tho150) to the standard facility→location
   convention.

**Future `.sln` writer must reverse this**: re-invert those eight files (and
kra32's on-disk header, if byte-faithful) to match their file conventions —
use `Permutations.inverseOf`.

## Test coverage (all PASS at handoff)

- `InstanceReaderTest` — reads all 136 `.dat` (strict token counts), reproduces
  all 128 `.sln` values under the standard convention with its own independent
  cost loop (127 direct + kra32 corrected→88700), verifies the missing-`.sln`
  set is exactly {esc32a–d, esc32h, esc64a, tai10a, tai10b}, and fails if any
  file matches only inverse orientation (= reader missed normalization).
- `InstanceRepositoryTest` — counts vs dataset facts (136 total, 28 tai,
  13 sko), sorted determinism, `getAll ≡ listNames`, exact-prefix family
  matching, unknown names throw.
- `QAPDatasetTest` — 136 pairs, 128 with solution, unmatched = known 8,
  per-pair name/size agreement, 26 tai solutions, `find` empty vs `get` throw.
- `SolutionVerifierTest` — 128/128 samples `isValid()=true` (8 of them via
  normalization), `isValid()` ≡ `verify()` everywhere, kra32 carries corrected
  88700, tampered-value negative controls, `CustomSolution` valid/invalid
  construction.
- `RandomizerTest` — bit-exact against Vigna's splitmix64.c reference vectors
  (independently cross-checked vs `java.util.SplittableRandom` at dev time);
  derivation goldens computed from the spec formula in Python (freezes the
  derivation, catches signed/overflow bugs); order-independence of `derive`;
  1000-stream distinctness and gamma conditioning; `nextInt`/`nextDouble`
  range + uniformity; shuffle multiset/bijection/determinism/6-ordering
  uniformity; 8 threads racing a shared `RandomSource` reproduce the
  sequential sequences exactly. No dataset dependency.

## Decisions log (why it is the way it is)

- **Long accumulation everywhere** — random tai100b solutions approach
  `Integer.MAX_VALUE`; entries up to ~2.9e8 per product (tai15b).
- **Internal arrays exposed, not copied** — hot-loop performance beats
  defensive copying; documented on every getter.
- **Strict token counts in readers** — format drift fails loudly instead of
  producing silently wrong instances.
- **Validity in the base class, auto-run at construction** — every solution
  object self-reports whether its claimed value is real.
- **Value-driven normalization over hardcoded lists** — orientation inversion
  triggers on evidence (value reproduces inverted), not on names; only the
  kra32 correction is name-guarded because a typo can't be detected otherwise.
- **No caching in repositories** — a solver run loads few instances; callers
  hold references.
- **Quirk knowledge lives in tests as exact expected sets** — a real reader
  bug cannot hide as a "quirk".
- **Derived streams instead of one shared RNG** — a single locked generator
  would be thread-safe but not reproducible (scheduling decides interleaving).
  One `RandomSource` derives per-island streams by id; thread confinement of
  each `Randomizer` gives lock-free thread safety and scheduling-independent
  determinism. Shared components (e.g. a future migration coordinator) get
  their own dedicated stream id.
- **Hand-rolled SplitMix64 over JDK generators** — `ThreadLocalRandom` is not
  seedable; `SplittableRandom`'s sequence is an implementation detail a future
  JDK may change; `java.util.Random` is a weak LCG with CAS overhead. ~40 lines
  of published, vector-verified algorithm buy bit-identical runs on every JDK.

## Next phase (not started)

Island-Model GA + SA hybrid per CLAUDE.md: solver-side `QAPSolution`
subclasses, delta (swap) evaluation — asymmetric instances need the general
two-orientation delta formula — population/island infrastructure, migration,
SA local search, presets per instance class (see the characteristics-CSV
section in CLAUDE.md).
