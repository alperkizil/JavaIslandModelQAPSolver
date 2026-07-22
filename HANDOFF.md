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
sigma scaling), `qapSolver.GA.Crossover.PartiallyMappedCrossover` (step (d):
PMX), `qapSolver.GA.Mutation.ReheatingSwapMutation` (step (e): SA-reheat
multi-swap), the `qapSolver.GA.Replacement` package (step (f): generational
+ steady-state), `qapSolver.GA.Elitism.BestKElitePreserver` (step (g)),
`qapSolver.GA.Improvement.NoOpImprovement` (step (h): the Null Object —
pure-GA baseline), and the
`qapSolver.Engine.Evaluation` package (step (b), complete trio: exact
baseline, caching LRU decorator, master–slave parallel evaluator), and the
`qapSolver.Engine.Termination` package (step (i): max-generations,
evaluation-budget, time-limit, stagnation).
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
java -cp out/main:out/test qapSolver.Engine.Evaluation.ExactEvaluatorTest
java -cp out/main:out/test qapSolver.Engine.Evaluation.CachingEvaluatorTest
java -cp out/main:out/test qapSolver.Engine.Evaluation.MultithreadedExactEvaluatorTest
java -cp out/main:out/test qapSolver.Engine.Termination.MaxGenerationsCriterionTest
java -cp out/main:out/test qapSolver.Engine.Termination.EvaluationBudgetCriterionTest
java -cp out/main:out/test qapSolver.Engine.Termination.TimeLimitCriterionTest
java -cp out/main:out/test qapSolver.Engine.Termination.StagnationCriterionTest
java -cp out/main:out/test qapSolver.GA.Initialization.RandomInitializerTest
java -cp out/main:out/test qapSolver.GA.Selection.TournamentSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.RouletteWheelSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.StochasticUniversalSamplingSelectorTest
java -cp out/main:out/test qapSolver.GA.Selection.SigmaScalingSelectorTest
java -cp out/main:out/test qapSolver.GA.Crossover.PartiallyMappedCrossoverTest
java -cp out/main:out/test qapSolver.GA.Mutation.ReheatingSwapMutationTest
java -cp out/main:out/test qapSolver.GA.Replacement.GenerationalReplacementTest
java -cp out/main:out/test qapSolver.GA.Replacement.SteadyStateReplacementTest
java -cp out/main:out/test qapSolver.GA.Elitism.BestKElitePreserverTest
java -cp out/main:out/test qapSolver.GA.Improvement.NoOpImprovementTest
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

### `qapSolver.Engine.Evaluation` — fitness evaluators

Concrete `FitnessEvaluator`s live at engine level (not under `qapSolver.GA`)
because every future engine (SA, island variants) evaluates the same way.
Pattern decision: caching is a *decorator* over any evaluator; parallelism is
a *leaf* implementation, never a wrapper — a parallel decorator would have to
hand the thread-confined `AlgorithmContext` (unsynchronized counters) to
workers, breaking the confinement that keeps runs replay-identical. Stacking
order is therefore fixed: cache outermost, e.g.
`CachingEvaluator(MultithreadedExactEvaluator)`.

| Class | Responsibility |
|---|---|
| `ExactEvaluator` | The baseline: sequential full O(n²) `ObjectiveFunction` evaluation per candidate on the engine thread, one `countFullEvaluation()` tick each, arrays moved zero-copy into the results, input order, no randomness. The reference every decorated/parallel stack must reproduce value-for-value. |
| `CachingEvaluator` | Decorator over any evaluator: per batch, cache hits answered directly, same-batch repeats answered by their first occurrence, only genuinely new permutations delegated (all-hit batches never invoke the inner evaluator). Ownership holds on every path (hits/repeats consume their own candidate's array zero-copy). Never counts evaluations itself — only the inner evaluator computes. Bounded LRU (capacity constructor param; hits refresh recency); keys are defensive copies, lookups transient no-copy keys. `getHits`/`getMisses`/`getCachedCount` are the per-family measurement that decides whether the decorator earns its place. Engine-thread only ⇒ always the outermost layer. |
| `MultithreadedExactEvaluator` | Master–slave leaf: batch partitioned into contiguous chunks over a fixed pool of named daemon workers (`workerCount` constructor param); workers compute pure `long` costs via the static `ObjectiveFunction` on the immutable instance and never see the context; the engine thread reassembles input-order results and does all counting and array moves. Replay-identical to sequential regardless of scheduling (exact costs, index order, zero randomness); memory safety via the executor's happens-before edges. Worker `RuntimeException`s rethrown as-is on the engine thread, evaluator stays usable; `shutdown()` idempotent, evaluate-after-shutdown throws. For single-island runs on large instances — island parallelism takes budget precedence. |

### `qapSolver.Engine.Termination` — termination criteria

Concrete `TerminationCriterion`s, engine-level like Evaluation. All are
read-only context checks (no randomness, counters untouched) evaluated
between generations — budget-style limits are therefore stopping *floors*:
the generation in flight completes, overshoot ≤ one generation. Target-value
and and/or combinators (Composite) are the planned future additions.

| Class | Responsibility |
|---|---|
| `MaxGenerationsCriterion` | Stop at `generation ≥ limit`; as the binding criterion a run evolves exactly `limit` generations beyond initialization (generation 0). The comparable-benchmark workhorse. |
| `EvaluationBudgetCriterion` | Stop at `fullEvaluations ≥ budget` (long). Machine-independent effort metric; cache hits never count (evaluator contract); generation-0 evaluation is on the budget. Full evaluations only — delta budgeting arrives with local search. |
| `TimeLimitCriterion` | Stop at `elapsedMillis ≥ limit`, clock from `context.start()` (generation-0 work included). The deliberately machine-dependent one — not for comparable experiments. Unstarted context fails loudly (ISE from the clock). |
| `StagnationCriterion` | Stop at `generationsSinceImprovement ≥ X` (strict improvements only — ties never reset; no incumbent ⇒ false). Knob pairing: races the reheating mutation — set X well above the mutation's threshold + cool-down, ideally several reheat cycles, or the run gives up before the escape mechanism acts. |

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

### `qapSolver.GA.Crossover` — crossover strategies

| Class | Responsibility |
|---|---|
| `PartiallyMappedCrossover` | PMX in the Watchmaker `ListOrderCrossover` shape: two uniform cut draws as an *ordered* pair ⇒ the exchanged segment may wrap around the array end, length uniform on 0..n−1; equal draws (probability 1/n) ⇒ empty segment ⇒ children are parent clones (kept as in the reference — deliberate clone-through stays the engine's rate path). Segment positions swap between the parents; outside positions keep the own parent's value, conflicts resolved by following the segment's value→value tables (value-indexed `int[]`, not boxed maps). Chains terminate structurally: each table is injective and a chain starts outside its image — no visited-set needed. Two children per call, exactly two `nextInt(n)` draws, O(n) total, parents read-only. |

### `qapSolver.GA.Mutation` — mutation strategies

| Class | Responsibility |
|---|---|
| `ReheatingSwapMutation` | Multi-swap mutation with an SA-style reheating schedule — the designated escape mechanism from local optima. Temperature in swaps-per-child: baseline `max(1, round(baselineFraction·n))`; when fully cooled *and* `generationsSinceImprovement ≥ stagnationThreshold`, reheats to `max(2, round(hotFraction·n))` and cools geometrically (`T ← max(baseline, T·coolingFactor)` per generation). Both tiers scale with n (basin size ∝ n per the measured ~0.25·n autocorrelation length; 0.25 is the data-backed `hotFraction` default). Persistent stagnation ⇒ periodic kick cycles; improvement resets the stagnation clock but never quenches a hot phase (elitism + incumbent make hot generations safe). Per-generation island state updated lazily on the first `mutate` of each generation; all same-generation children get the same k; per child exactly 2k draws (`nextInt(n)`, `nextInt(n−1)`+shift ⇒ k distinct-position transpositions — the random-walk model the correlation lengths were measured with). n=1 ⇒ identity, zero draws. `getCurrentSwaps()` exposed for observability. All constants constructor-injected starting points, to be benchmarked. |

### `qapSolver.GA.Replacement` — survivor selection strategies

| Class | Responsibility |
|---|---|
| `GenerationalReplacement` | Full turnover: offspring are the next generation, every parent dies; fresh `Population`, input untouched. Requires λ = μ, loud `IllegalStateException` otherwise (config error, not papered over; (μ,λ)/(μ+λ) truncation are future siblings). No survivor pressure by design — breeding is selection's decision, survival of the old best is the bracket's. Deterministic, no randomness. |
| `SteadyStateReplacement` | GENITOR-style replace-worst, in place: each child in input order replaces the *current* worst member — the batch is a sequence of λ birth events, so later children may evict earlier ones; λ = 1–2 with large μ through the engine = the classic steady-state GA. Acceptance is *unconditional* (worse-than-everyone still enters): if-better acceptance would double-dip on selection pressure and re-implement the bracket's protection; unconditional turnover keeps λ genotypes/generation flowing — what keeps plateau families moving sideways. Worst ties first-index; deterministic, O(λ·μ), no randomness. |

### `qapSolver.GA.Elitism` — elite preservation strategies

| Class | Responsibility |
|---|---|
| `BestKElitePreserver` | Best-k elitism. Extract: references to the k lowest-fitness members, best first, (fitness, first-index) tie-break; k = 0 ⇒ elitism off (no separate NoOp class); k ≥ μ throws at extract. Reinsert per elite: presence judged by permutation content (`samePermutationAs`, never fitness); missing ⇒ overwrite the worst among unprotected slots — found/reinserted slots stay protected for the rest of the call (all-tied populations would otherwise evict elite #1 for elite #2), and duplicate-genotype elites collapse to one survivor. Deterministic, consumes no randomness. |

### `qapSolver.GA.Improvement` — local improvement strategies

| Class | Responsibility |
|---|---|
| `NoOpImprovement` | Local improvement switched off — the Null Object of slot (h): returns the input batch as-is (same list, same references, zero work, nothing counted, no randomness). Composing it makes the engine a plain non-memetic GA — the baseline real improvers (2-swap descent, SA — blocked on the delta utility) are measured against. |

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
- `ExactEvaluatorTest` — hand-computed n=2 anchor (identity 70, transposition
  60 by pencil-and-paper); bulk contract on random batches at n=3/10/30
  (values vs direct `ObjectiveFunction` calls, input order, zero-copy array
  identity per slot); evaluation counting across repeated batches (5 then
  5+3); empty-batch and n=1 edges; no randomness consumed (stream-position
  agreement); timer (invocations = batches). Synthetic instances only — no
  dataset dependency.
- `CachingEvaluatorTest` — constructor validation (null inner, capacity < 1);
  miss path (exact values, order, zero-copy ownership, delegation observed
  via a recording inner evaluator); hit path (same content as fresh arrays:
  zero inner invocations, zero new evaluation counts, results own the new
  arrays); mixed batches (inner sees exactly the misses in input order);
  same-batch repeats (2 unique of 4 candidates ⇒ 2 evaluations, every slot
  owns its own array, one cache entry per content); LRU both ways (oldest
  evicted at capacity; hit-refreshed recency flips the victim); equivalence
  sweep vs undecorated ExactEvaluator on overlapping batches (identical
  fitnesses, 6 vs 11 evaluations); no randomness consumed; timer (decorator
  counts every call, inner only delegated ones). Synthetic instances only —
  no dataset dependency.
- `MultithreadedExactEvaluatorTest` — constructor validation; equivalence
  with sequential `ExactEvaluator` across workers {1,3,8} × batch sizes
  {0,1,2,7,8,25} (slot-exact fitness, zero-copy identity, counting) plus a
  100×n=40 stress batch on 4 workers; the sanctioned stack
  `CachingEvaluator(MultithreadedExactEvaluator)` (repeat batch: 1 new
  evaluation, cached values identical through the stack, counters); no
  randomness consumed; worker-exception propagation (wrong-length
  permutation surfaces as the objective's IAE, evaluator usable after);
  shutdown idempotence and evaluate-after-shutdown throwing; timer.
  Synthetic instances only — no dataset dependency.
- `MaxGenerationsCriterionTest` — constructor validation; exact stop
  boundary (false through limit−1, true at/beyond the limit); read-only
  (counters and stream untouched); timer.
- `EvaluationBudgetCriterionTest` — constructor validation (long budgets
  beyond int range legal); exact boundary at the counted budget; read-only;
  timer.
- `TimeLimitCriterionTest` — constructor validation; unstarted context fails
  loudly (ISE); generous limit doesn't stop a fresh run, 1 ms limit stops
  after a bounded 2 ms busy-wait; read-only; timer.
- `StagnationCriterionTest` — constructor validation; no incumbent ⇒ never
  stops; exact boundary at X stagnant generations; strict improvement resets
  the clock (stop exactly X after it); a value tie does NOT reset; read-only;
  timer.
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
- `PartiallyMappedCrossoverTest` — recombination contract (exactly two
  children, fresh owned arrays — never a parent's or each other's, parents
  byte-unchanged, valid permutations); bit-exact replay against an
  independent boxed transliteration of the Watchmaker reference algorithm
  (3 seeds × 5 sizes incl. n=2 and n=60, wrap cases included) with final
  stream-position agreement (exactly two draws); structural semantics via
  mirrored cut-point draws (segment carries the other parent's values,
  conflict-free outside positions keep the own parent's, 50 seeds at n=15);
  empty-segment clone case (n=5 seed sweep, ≥20 degenerate draws verified
  clone-exact); chain-repair stress on rotation and reversal parents (maximal
  mapping chains, n=30); same-seed determinism vs cross-seed difference;
  n=1 edge; timer (invocations = pairs). Synthetic parents only — no dataset
  dependency.
- `ReheatingSwapMutationTest` — constructor validation (fractions ∉ (0,1],
  hot < baseline, α ∉ (0,1), S < 1, NaN-proof); mutation contract (in-place
  on the same array, permutation validity, baseline at n=20 ⇒ exactly one
  transposition ⇒ exactly 2 changed positions); bit-exact replay of the
  2k-draw swap sequence over two same-generation children with stream-position
  agreement; n-scaling of both tiers (n=20 vs n=100: baseline 1 vs 2, hot 5
  vs 25); full reheat cycle against a hard-coded temperature trace
  ({1,1,1,25,13,6,3,2,1,25} for α=0.5, S=3 — reheat at threshold, geometric
  cooling, re-reheat under persistent stagnation); improvement resets the
  stagnation clock (no reheat at the would-be generation) but does not quench
  an in-progress cooling phase; n=1 identity with zero draws consumed;
  same-seed determinism vs cross-seed difference; timer (invocations =
  children). Synthetic instances only — no dataset dependency.
- `GenerationalReplacementTest` — full turnover at λ = μ (fresh Population,
  offspring references slot-for-slot in input order, input object and members
  untouched); λ ≠ μ throws both directions; no randomness; timer.
- `SteadyStateReplacementTest` — in-place replace-worst (same object back,
  only the worst slot swapped); unconditional acceptance (worse-than-everyone
  child still evicts); sequential birth semantics (later children evict
  earlier ones; insertion order changes the survivor); first-index worst-tie
  break; λ > μ churn against a hand-traced state; μ preserved; no randomness;
  timer.
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
- `NoOpImprovementTest` — pure identity (same list object, member references
  and order untouched); empty batch; context untouched (no counts, no
  randomness, no incumbent); timer.
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
- **PMX in the reference's shape, primitive inside** — the port keeps
  Watchmaker's observable semantics (ordered wraparound cut points, segment
  length uniform on 0..n−1, empty segment ⇒ parent clones) so the harness can
  pin the operator bit-exactly to an independent transliteration of the
  reference; the boxed List/HashMap machinery is replaced by value-indexed
  `int[]` mapping tables on the breeding path. The repair chains need no
  cycle guard: each table is injective and a chain starts at a value outside
  the table's image, so termination is structural, not defensive.
- **Steady-state accepts unconditionally** — replace-worst takes every child,
  even one worse than the whole pool. Acceptance-if-better was rejected
  because each pressure source stays single-owned: parent selection owns
  selective pressure, the elitism bracket owns protection of the best, and
  replacement owns turnover only. The batch is processed as λ sequential
  birth events (later children can evict earlier ones), which makes the
  generational engine reproduce a classic steady-state GA exactly at
  λ = 1–2.
- **Termination checked between generations ⇒ budgets are floors** — the
  engine consults criteria only at generation boundaries, so evaluation and
  time limits overshoot by at most one generation; that is accepted rather
  than adding mid-generation abort paths. The stagnation criterion is
  documented as racing the reheating mutation: its X must exceed the
  mutation's stagnation threshold plus cool-down (ideally several reheat
  cycles), or runs give up before the escape mechanism has acted.
- **Evaluators: caching decorates, parallelism is a leaf** — the two planned
  evaluator features compose by different patterns, forced by thread
  confinement. Caching wraps any inner evaluator (same abstract type, engine
  thread only). A "parallel decorator" is impossible under the contract:
  inner evaluators call `context.countFullEvaluation()`, and the context's
  counters are deliberately unsynchronized/thread-confined — so the
  master–slave evaluator is a concrete leaf whose workers compute pure costs
  from the immutable instance, while the engine thread reassembles
  input-order results and does all counting. Cache goes outermost so its map
  never needs locks. Composite was considered and rejected for evaluators
  (nothing to merge — one exact fitness per candidate from one authority);
  it remains the right shape for termination-criterion and/or combinators.
- **Mutation as SA reheating, strengths as fractions of n** — the escape
  mechanism is temperature-shaped (reheat on stagnation only after full
  cool-down, then geometric cooling) rather than a monotone stagnation ramp,
  so kicks are episodic and self-limiting; persistent stagnation yields
  periodic cycles instead of a permanently hot island. Improvements reset
  the stagnation clock but deliberately do not quench cooling — elitism and
  the incumbent copy make hot generations safe. Both strength tiers scale
  with n because the measured autocorrelation length is ~0.25·n across all
  families (basin size grows with n): absolute swap counts would under-kick
  large instances. Defaults are starting points for benchmarking, all
  constructor-injected; per-island hot/cold variants come free via
  construction.
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
- **Every GA slot now has at least one concrete implementation** — next: the
  end-to-end smoke run on a small closed instance (which doubles as the
  deferred engine-composition test).
  Termination extras (target-value criterion, and/or Composite combinators)
  as needed.
- Delta (swap) evaluation utility — the general two-orientation formula for
  the 37 asymmetric instances — prerequisite for real `LocalImprovement`
  implementations (2-swap descent, SA).
- Island layer (`qapSolver.Island`): coordinator driving
  `initialize()`/`step()`, the Migration abstraction (designed there, with
  its collaborators), per-island contexts and streams, presets per instance
  class (see the characteristics-CSV section in CLAUDE.md).
