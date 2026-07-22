# HANDOFF — implementation state

Session handoff for **JavaIslandModelQAPSolver** (July 2026). Working rules and
dataset background live in [CLAUDE.md](CLAUDE.md); this file records what has
been built so far, the decisions behind it, and how to run it.

## Status

Foundation layer complete: dataset model, readers/repositories, objective
function, solution verification. All 136 QAPLIB instances read correctly; all
128 sample solutions load, normalize, and verify clean. `qapSolver.Random`
provides seeded, thread-reproducible randomness. The solver skeleton is in
place: `qapSolver.Engine` (metaheuristic-generic runtime) and `qapSolver.GA`
(the composed generational memetic GA plus one abstract class per step). First
concrete operators are landing, all harness-tested:
`qapSolver.GA.Initialization.RandomInitializer` (step (a)), the
`qapSolver.GA.Selection` package (step (c): tournament, roulette wheel, SUS,
sigma scaling), and `qapSolver.GA.Elitism.BestKElitePreserver` (step (g)).
The skeleton itself is still
compile-verified only; its dedicated test harness remains deferred and should
land with the next concrete steps.

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
java -cp out/main:out/test qapSolver.GA.Initialization.RandomInitializerTest
java -cp out/main:out/test qapSolver.GA.Selection.TournamentSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.RouletteWheelSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.StochasticUniversalSamplingSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.SigmaScalingSelectorTest
java -cp out/main:out/test qapSolver.GA.Elitism.BestKElitePreserverTest
```

Test harnesses default to `QAPData/qapdata` / `QAPData/qapsoln` relative to the
working directory (repo root); both can be overridden via args.

## Architecture

### `qapSolver.Model` — domain types

| Class | Responsibility |
|---|---|
| `QAPInstance` | Immutable instance: name, size n, matrices A and B as read from the `.dat` (A first). No symmetry/zero-diagonal assumptions. Getters expose internal arrays by design (hot loops) — do not mutate. |
| `QAPSolution` *(abstract)* | Full common solution state: `instanceName`, objective value, 0-based permutation, size, and `isValid()`. Constructor takes the `QAPInstance`, validates the permutation (bijection, length = n) and **auto-verifies** via `SolutionVerifier` — valid ⇔ claimed value reproduced by the objective. Subclasses only express provenance. The verified **boundary** type (run results, future `.sln` writer) — *not* the evolving individual: that is `qapSolver.Engine`'s Candidate/EvaluatedCandidate pair, because per-construction O(n²) verification is exactly what the hot loop must not pay. |
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

### `qapSolver.Engine` — metaheuristic-generic runtime

| Class | Responsibility |
|---|---|
| `AlgorithmEngine` *(abstract)* | Lifecycle base of every engine: `final` `initialize()`/`step()`/`run()`/`shouldTerminate()` around `protected` `doInitialize()`/`doStep()` hooks. Generation 0 = evaluated initial population; the counter advances *before* the step body (stamps carry the in-progress generation). Owns the `TerminationCriterion` slot (stop flag checked first, in the loop only). `getBestSolution()` converts the incumbent to an auto-verifying `CustomSolution`. Island coordinators drive `initialize()`/`step()` directly and own run-end semantics. |
| `AlgorithmContext` | Per-run (= per-island) state hub, state-not-config: instance, `Randomizer` stream, generation + full/delta evaluation counters, incumbent (private copy + found-at stamps) behind the `offerIncumbent` choke point (strict improvement only, fires `onNewBest`), derived `generationsSinceImprovement()`, `start()`-gated clock, observer dispatch, and volatile `requestStop()` — the only cross-thread member. |
| `AlgorithmStep` *(abstract)* | Base of every step: built-in wall-clock timing (total nanos + invocation count). Step abstracts expose `final` timed entry points delegating to `protected doX` hooks, so implementations are always measured. Stateful ⇒ strictly one step instance per engine, thread-confined. Invocations mean *calls at the step's own granularity* (pairs, children, batches, phases); cross-step comparison uses totals. |
| `Candidate` | Mutable unevaluated permutation — the breeding scratch. Owns its array, hot-path trust (null check only). Single-owner hand-off: creator → mutation → evaluator, which consumes it. |
| `EvaluatedCandidate` | Immutable permutation + exact `long` fitness. The evaluator moves a Candidate's array in (zero-copy evaluation path); `toCandidate()` copies back to mutable land; `samePermutationAs`/`permutationHash` for dedup by permutation content (never fitness equality — tie-heavy families). `equals`/`hashCode` keep identity semantics. |
| `Population` | Mutable pool of immutable members — the type-state boundary (anything inside is evaluated; the unevaluated batch is a plain `List<Candidate>`, never a Population). Enforces non-null members and uniform n. Minimization-native `best()`/`worst()` (+ indices, first-index ties); `set` returns the evicted member. |
| `FitnessEvaluator` *(abstract)* | Bulk `List<Candidate>` → `List<EvaluatedCandidate>`. Contract: ownership transfer, input-order results (what keeps parallel evaluation replay-identical), exact values, one `countFullEvaluation()` per actual O(n²) computation (cache hits uncounted). The seam for the future caching decorator and master–slave evaluator. |
| `TerminationCriterion` *(abstract)* | Read-only stop check between generations; must not consume randomness (would shift the stream and break replay). Never re-checks the external stop flag — the engine loop owns that. |
| `EvolutionObserver` *(abstract)* | Read-only no-op hooks: `onRunStart`, `onGenerationComplete(ctx, pop)` (generation 0 included), `onNewBest` (strict improvements only), `onRunEnd` (fired by `run()`; external drivers own it). Engine-thread only. |

### `qapSolver.GA` — the genetic algorithm

| Class | Responsibility |
|---|---|
| `GeneticAlgorithm` *(final)* | The composition: generational memetic cycle over the eight slots — extract elites (g) → bulk-select 2λ parents (c) → pair-breed with per-pair crossover rate, clone-through via `toCandidate()`, truncate to exactly λ (d) → mutate every child (e) → evaluate + offer (b) → improve + offer (h) → replace (f) → reinsert (g). Engine-side contract checks (batch sizes, ≥1 child per crossover call, μ preserved by replacement and reinsertion) throw `IllegalStateException` immediately. μ comes from the initializer's batch; numeric config is only `offspringCount` (λ) and `crossoverRate`. All slots required — "off" is a NoOp step, never null. A differently shaped cycle (steady-state, SA) is a sibling under `AlgorithmEngine`. |
| `PopulationInitializer` *(abstract)* | Step (a): unevaluated generation-0 batch; the returned list's size *is* μ for the whole run. |
| `ParentSelector` *(abstract)* | Step (c): bulk selection of `count` parent references (repeats normal, population untouched); per-generation setup paid once. Minimization-native: comparison-based schemes fit; roulette needs an impl-supplied transform. |
| `CrossoverOperator` *(abstract)* | Step (d): two evaluated parents → 1+ fresh unevaluated children. Pure — the rate lives in the engine's breeding loop. QAP note: position-preserving recombination matches assignment semantics; order-based (OX) preserves the TSP invariant. |
| `MutationOperator` *(abstract)* | Step (e): in-place variation of a `Candidate` — stale fitness is unrepresentable by signature. Rate/strength operator-internal; engine calls unconditionally. Guidance recorded: autocorrelation length ~0.25·n ⇒ n/4-swap hot kick, 1–2 swaps cold. |
| `ReplacementStrategy` *(abstract)* | Step (f): survivor selection, exactly μ out; in-place steady-state or fresh generational both legal. Elitism contractually excluded (the bracket handles it). Dedup variants compare permutations, never fitness. |
| `ElitePreserver` *(abstract)* | Step (g): two-phase bracket — `extract` (references-as-snapshots) before breeding, `reinsert` after replacement — composing with any replacement strategy; empty extract = elitism off; stateless between phases; both phases share one timer (two invocations per generation). |
| `LocalImprovement` *(abstract)* | Step (h): bulk memetic slot between evaluation and replacement; budget policy internal (all/top-k/stagnation-triggered). Exact-fitness results in input order; improvement is the goal, not a guarantee (best-visited convention). Mutable scratch inside, immutable boundary out; honest counting (`countDeltaEvaluations` vs `countFullEvaluation`). Prerequisite: the general two-orientation delta utility (37 asymmetric instances). |

### `qapSolver.GA.Initialization` — initialization strategies

Concrete `PopulationInitializer`s. Package convention set here: `qapSolver.GA`
holds the framework (abstract steps + the composed engine); concrete operators
live in per-role subpackages.

| Class | Responsibility |
|---|---|
| `RandomInitializer` | Uniform random initialization: μ (sole constructor parameter, ≥ 1) candidates, each a fresh identity array Fisher–Yates-shuffled on the context's stream — independent uniform draws from all n! permutations. No operator-held randomness: the batch is a pure function of (master seed, stream id). Duplicates permitted (duplicate-free is a future sibling strategy). |

### `qapSolver.GA.Selection` — parent selection strategies

| Class | Responsibility |
|---|---|
| `TournamentSelector` | Probabilistic tournament (t ≥ 1, p ∈ (0, 1]): per pick, t uniform with-replacement contestants, ordered best-first (fitness ascending, ties by draw order), cascade accepts with probability p, last contestant as fallback. p = 1 ⇒ classic deterministic tournament; t = 1 ⇒ uniform selection; t > μ legal (saturates to always-best). Comparison-based ⇒ scale-free; the per-island pressure knob. |
| `RankWeights` *(package-private)* | Shared per-generation linear-ranking table for the two rank samplers: unique worst-first ranking (fitness desc, index asc — total order, tie-safe), weight (2−s) + 2(s−1)·r/(μ−1) summing to μ, cumulative array; binary-search `sample` (roulette) and monotone `advance` (SUS); zero-weight ranks are unreachable by construction. |
| `RouletteWheelSelector` | Independent spins over rank weights, s ∈ [1, 2] (1 = uniform; 2 = strongest, worst weight exactly 0 ⇒ never drawn). Rank basis keeps the wheel scale-free on QAP. Consumes exactly count doubles. |
| `StochasticUniversalSamplingSelector` | One start double + count evenly spaced pointers over the same rank table: every member's copy count is its expectation floored or ceiled (minimal sampling variance). Result is Fisher–Yates-shuffled (count−1 ints) so the rank-ordered walk doesn't self-pair under the engine's consecutive pairing. |
| `SigmaScalingSelector` | Standalone Watchmaker-style sigma scaling, roulette-sampled: per generation w = 1 + (mean−f)/2σ (minimization form), floored at 0.1 when ≤ 0, σ = 0 ⇒ uniform. Parameterless — pressure adapts to population statistics; the one proportional scheme that stays meaningful on QAP's compressed relative spreads. |

### `qapSolver.GA.Elitism` — elite preservation strategies

| Class | Responsibility |
|---|---|
| `BestKElitePreserver` | Best-k elitism. Extract: references to the k lowest-fitness members, best first, (fitness, first-index) tie-break; k = 0 ⇒ elitism off (no separate NoOp class); k ≥ μ throws at extract. Reinsert per elite: presence judged by permutation content (`samePermutationAs`, never fitness); missing ⇒ overwrite the worst among unprotected slots — found/reinserted slots stay protected for the rest of the call (all-tied populations would otherwise evict elite #1 for elite #2), and duplicate-genotype elites collapse to one survivor. Deterministic, consumes no randomness. |

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
- `RandomInitializerTest` — constructor validation (μ < 1 throws); batch shape
  (size μ, valid 0-based permutations, per-candidate owned arrays, no content
  duplicates at n=20/μ=30); bit-exact stream replay from an independently
  derived same-seed stream (candidate k = identity + k-th shuffle, in order,
  no extra draws — pins that all randomness is the engine-owned context
  stream); same-seed determinism vs cross-seed difference; n=1 edge;
  3!-ordering uniformity through the operator; step-timer bookkeeping.
  Synthetic instances only — no dataset dependency.
- `TournamentSelectorTest` — constructor validation (t, p, NaN-proof); bulk
  contract (exact count, references only, population untouched, count = 0);
  bit-exact stream replay of draw + stable sort + cascade with final
  stream-position agreement; same-seed determinism / cross-seed difference;
  pressure (t = 3 vs t = 1 buckets; the p knob via P(best) = 0.25 + 0.5p on
  μ = 2; t = 50 ≫ μ saturation); timer. Synthetic members only.
- `RouletteWheelSelectorTest` — s validation; bulk contract; bit-exact replay
  against an independent transliteration of the ranking spec; s = 2 draws the
  zero-weight worst exactly never and the best ≈ 40%; s = 1 uniform; μ = 1
  edge; determinism; timer. Synthetic members only.
- `StochasticUniversalSamplingSelectorTest` — s validation; bulk contract;
  bit-exact replay (table + pointer walk + shuffle); the SUS guarantee across
  seeds (exact counts {4,3,2,1,0} where expectations are integers, floor/ceil
  bounds at count = 7); shuffle breaks fitness monotonicity; μ = 1 edge;
  determinism; timer. Synthetic members only.
- `SigmaScalingSelectorTest` — bulk contract; bit-exact replay (mean/σ,
  floored weights, spins); σ = 0 uniform; > 2σ outlier floored (rare, never
  extinct); compressed-spread pressure (0.9% spread → ~8× best/worst where a
  raw wheel would flatten); μ = 1 edge; determinism; timer. Synthetic members
  only.
- `BestKElitePreserverTest` — constructor validation (k < 0 throws, 0/1 legal);
  extract pick and best-first order with first-index tie-break, reference
  snapshots, read-only population, k = 0 empty, k ≥ μ throws (k = μ−1 legal);
  reinsert leaves present-by-reference and present-by-content populations
  bit-identical; missing elites overwrite worst slots in order with size
  preserved; the all-tied eviction trap (protected slots — both elites
  survive, found slots not overwritten); duplicate-genotype collapse (one
  survivor, no second slot burned); no-slot-left guard throws; both phases on
  one timer (two invocations per generation). Synthetic members with
  fabricated fitnesses — no dataset dependency.
- Engine/GA skeleton: **compile-verified only** for now — its dedicated
  harness was deliberately deferred and should land with the first concrete
  steps (context bookkeeping, candidate/population invariants, lifecycle
  guards, stubbed-step call-order test).

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
- **Typed step slots over a uniform pipeline** — one abstract class per GA
  step with a typed contract, composed by the engine in a fixed cycle.
  Compile-time data flow beats blackboard coupling through shared state; a
  differently shaped cycle is a sibling engine, not a reordering.
- **Candidate/EvaluatedCandidate type-state split** — fitness exists only on
  the immutable evaluated form; mutation accepts only the mutable unevaluated
  form, so a stale fitness is unrepresentable. Evaluation moves arrays rather
  than copying (deliberate copies only at breeding, `toCandidate()`, and the
  incumbent snapshot). `QAPSolution` stays the boundary type: per-construction
  O(n²) auto-verification is what the hot loop must not pay.
- **Self-timing steps** — `AlgorithmStep` wraps every public step entry point
  final-with-finally around a protected hook and accumulates wall time;
  measurement is a framework property, not engine etiquette. Consequence:
  step instances are stateful ⇒ per-engine, never shared across islands.
- **Lifecycle final in the base** — clock start, notification order, and
  advance-before-step-body are written once in `AlgorithmEngine`; generation
  stamps cannot drift and every engine behaves identically under a
  coordinator.
- **Incumbent single-writer** — only the engine offers candidates, only via
  `offerIncumbent` (strict improvement, private copy, three found-at stamps,
  then `onNewBest`). Steps never touch incumbent state.
- **Rate placement asymmetry** — the crossover rate is an engine breeding
  parameter ("no crossover" is a different data path: clone-through); mutation
  rate/strength is operator-internal ("no mutation" is identity, no engine
  branch).
- **Elitism as a two-phase bracket** — extract before breeding, reinsert after
  replacement; composes with any `ReplacementStrategy`, which stays
  contractually elitism-free; an empty extract turns elitism off.
- **Engine-side contract checks** — batch sizes, ≥1 crossover child, and μ
  preservation are verified at every hand-off and throw immediately; a buggy
  step cannot silently corrupt a run.
- **Selection: comparison, rank or adaptive — never raw proportional** —
  tournament is the probabilistic cascade (t, p); roulette and SUS sample
  shared linear rank weights (s ∈ [1, 2]) because raw fitness-proportional
  pressure collapses on QAP's compressed relative spreads; sigma scaling is
  the sanctioned proportional form (population-statistics normalization,
  deliberately standalone in Watchmaker shape rather than a weighting×sampler
  matrix). SUS shuffles its rank-ordered picks so the engine's consecutive
  pairing doesn't self-pair.
- **Concrete operators in per-role subpackages** — `qapSolver.GA` keeps the
  framework (abstract steps + the composed engine); implementations group by
  role (`qapSolver.GA.Initialization` first, more as steps get concrete), so
  strategy families stay together as they multiply.
- **Migration deferred to the island layer** — it is not a GA step (the
  engine would never call it), and its contract depends on island-layer
  decisions not yet made (synchronous vs mailbox exchange, topology,
  emigrant/immigrant split). The seams are ready: externally driven
  `initialize()`/`step()`, `Population.set`, immutable shareable members, and
  a dedicated coordinator stream id in `RandomSource`.

## Next phase (not started)

- Engine/GA test harness (deferred from the skeleton step): context
  bookkeeping, candidate/population invariants, lifecycle guards, then a
  stubbed-step test pinning the engine's call order and contract checks.
- Remaining concrete steps, each its own step-by-step piece:
  exact evaluator over `ObjectiveFunction`, a
  position-preserving crossover, swap mutation, generational replacement,
  NoOp improvement, and termination criteria
  (max-generations / eval-budget / wall-clock / target-value / stagnation
  with and/or combinators) — then an end-to-end smoke run on a small closed
  instance.
- Delta (swap) evaluation utility — the general two-orientation formula for
  the 37 asymmetric instances — prerequisite for real `LocalImprovement`
  implementations (2-swap descent, SA).
- Island layer (`qapSolver.Island`): coordinator driving
  `initialize()`/`step()`, the Migration abstraction (designed there, with
  its collaborators), per-island contexts and streams, presets per instance
  class (see the characteristics-CSV section in CLAUDE.md).
