# CLAUDE.md

## Working rules (user's standing commands)

The user drives this project step by step, using Claude like an IDE. Obey these
at all times:

1. **No yapping.** Describe only relevant things; stay problem-focused. No
   filler, no restating what the user already knows, no unsolicited surveys of
   options.
2. **Never start coding until explicitly told to.** Discussion and design first;
   code only on an explicit "go" from the user.
3. **Never assume.** If anything is ambiguous or underspecified, ask the user
   instead of picking a default silently.
4. **Step-by-step development.** The GA solver is built incrementally in small,
   user-directed steps — do the step that was asked, then stop; don't run ahead.
5. **No pushing or PR creation without asking.** Commit each step locally.
   Before any push or PR, always ask the user first — act only on their
   explicit go-ahead.

## Design principles

- Object-oriented design with a modular approach.
- Maximum code reusability.
- Separate individual components as much as possible — high cohesion, low
  coupling: each module does one thing and exposes a minimal interface.

## Project

**JavaIslandModelQAPSolver** — an Island-Model GA + Simulated Annealing hybrid
solver for the Quadratic Assignment Problem, to be written in **Java**.
Foundation layer (Model/Reader/Objective) is implemented — **see
[HANDOFF.md](HANDOFF.md)** for the current implementation state, architecture,
build/run commands, and the decisions log. No GA/SA solver code yet.

- Objective: minimize `cost(p) = Σᵢ Σⱼ A[i][j] · B[p(i)][p(j)]` over permutations `p`.
- Instances: `QAPData/qapdata/*.dat` (136 files: `n`, matrix A, matrix B).
- Reference solutions: `QAPData/qapsoln/*.sln` (128 files: `n`, value, permutation).
- 108 instances have proven optima; 28 are open (best known only) — see README.md.
- **Read the "Data quirks" section of README.md before writing any loader or
  evaluator** (kra32 header typo → true optimum 88700, eight inverse-convention
  `.sln` files, 0-based tai40a, comma-separated permutations, degenerate esc16f,
  nonzero diagonals in bur/tai64c/tai256c, 37 asymmetric instances).
- Java notes: accumulate costs in `long` (random tai100b solutions approach
  `Integer.MAX_VALUE`); keep hot loops primitive (no boxing/streams); asymmetric
  instances need the general two-orientation swap delta; never skip diagonal terms.
- **Toolchain: JDK 11+.** Target Java 11 language level and APIs only — no
  post-11 features (records, switch expressions, text blocks, sealed types,
  pattern-matching `instanceof`). `var` is allowed.

## Initial dataset analysis (July 2026)

Established when first analyzing this folder; status verified against the QAPLIB
maintainer's pages (miguelanjos.com "QAPLIB Challenge"; COR@L QAPLIB news).

- The dataset folder `QAPData/` is the Edinburgh DataShare deposit
  **DS_10283_4390** of QAPLIB. The bundled README PDF is the **April 2012**
  status snapshot (Hahn & Anjos).
- 136 instances across 15 families; 128 `.sln` files. Eight instances have no
  `.sln`: esc32a–d, esc32h, esc64a (optimal permutations are printed in the PDF)
  and tai10a/b (optima computed exactly in this project by enumeration).
- **Solved to optimality (108):** entire families bur, chr, els, esc, had, kra,
  lipa, nug, rou, scr, ste; tho30; and tai10a/b, tai12a/b, tai15a/b, tai17a,
  tai20a/b, tai25a/b, tai30b, tai64c. Post-2012 closures: tai35b, tai40b
  (2015, arXiv:1510.02065) and tai30a, sko42 (2021, arXiv:2101.09629,
  NewtBracket SDP). Milestones: esc family closed by Fischetti–Monaci–Salvagnin
  MILP (~2011); nug30 by Anstreicher et al. (2000); esc128 (n=128) is the
  largest instance ever solved exactly.
- **Open (28)** — best known value only, gap vs. best lower bound per the 2012 README:
  - sko (12): sko49, sko56, sko64, sko72, sko81, sko90, sko100a–f — gaps ~5.4–5.9%
  - tai (12): tai35a, tai40a, tai50a, tai60a, tai80a, tai100a (a-series, gaps
    8.5–24.9%); tai50b, tai60b, tai80b, tai100b, tai150b (b-series, 10.8–13.8%);
    tai256c (2.0%)
  - tho40 (6.7%), tho150 (6.3%); wil50 (3.5%), wil100 (3.2%)
- Smallest open instance is sko49 (everything n ≤ 42 is closed). The hardest are
  Taillard's uniform-random a-series (tai100a bound ~25% below best known).
  tai256c has only a 2% gap but is far too large for exact methods.
- The objective was verified computationally: 127/128 `.sln` values reproduce
  exactly under `cost(p) = Σ A[i][j]·B[p(i)][p(j)]` (the exception is the kra32
  header typo; see README.md quirks for the 8 inverse-convention files).
- Caveat: `.sln` values for open instances are 2012 records; some large tai-a
  best knowns have since been improved slightly — check current QAPLIB/Taillard
  pages before citing them as records.

## qaplib_characteristics.csv — column reference

One row per instance, sorted by (family, n, name). Convention: **A = first matrix
in the `.dat` file, B = second**; which is "flow" vs "distance" varies by family,
so structural metrics are reported for both. Sampling columns used fixed seed 42
(200 random solutions; 2000-step random walk) — estimates, not exact values.

### Identity

| Column | Meaning |
|---|---|
| `name` | Instance ID = `.dat` filename without extension (e.g. `tai60a`). |
| `family` | Alphabetic prefix before the first digit (`bur`, `chr`, `els`, `esc`, `had`, `kra`, `lipa`, `nug`, `rou`, `scr`, `sko`, `ste`, `tai`, `tho`, `wil`). |
| `n` | Problem size (facilities = locations). Search space is `n!`. |

### Matrix structure

| Column | Meaning |
|---|---|
| `sym_A`, `sym_B` | `True` if the matrix equals its transpose. If either is `False` the instance is asymmetric (all bur, all lipa, tai-b series — in tai-b it is B) → general delta formula required. |
| `diag_A_nonzero`, `diag_B_nonzero` | `True` if the matrix has any nonzero diagonal entry (bur26a–h, tai64c, tai256c). Adds permutation-dependent term `Σᵢ A[i][i]·B[p(i)][p(i)]` — evaluators must include diagonals. |
| `sparsity_A_pct`, `sparsity_B_pct` | % of **off-diagonal** entries equal to zero. High sparsity (esc: 77–100%) → plateau-heavy landscape. |
| `dom_A`, `dom_B` | Vollmann–Buffa dominance = `100·std/mean` over all n² entries. ≈60 = uniform-random noise (tai-a); >150 = clustered/exploitable structure (tai-b ≈ 320). `NaN` only for esc16f's all-zero matrix. |
| `distinct_A`, `distinct_B` | Count of distinct off-diagonal values. Few values (grids: 6–10; tai-c: 14–41) → many exact fitness ties → dedup must compare permutations, not fitness. |
| `max_A`, `max_B` | Largest entry per matrix. Largest product in library ≈ 2.9e8 (tai15b): deltas fit `int`, totals need `long`. |

### Reference value

| Column | Meaning |
|---|---|
| `bks` | Best known solution value. From `.sln` files with corrections: kra32 → 88700; esc32a–d/h, esc64a from README PDF; tai10a = 135028 and tai10b = 1183760 computed exactly by enumeration. Proven optimal for 108 instances; best-known-only for the 28 open ones. |

### Landscape probes (seed 42)

| Column | Meaning |
|---|---|
| `rand_mean`, `rand_min` | Mean / best objective over 200 uniformly random permutations. `rand_min` = trivial baseline any search must beat immediately. |
| `rand_gap_pct` | `100·(rand_mean − bks)/bks` — total exploitable structure. ~2% on lipa-a (flat, needle-in-haystack), 14–27% on tai-a (the hard/open class), 150–2500% on chr/els/esc (local search does the heavy lifting). Empty for esc16f (bks = 0). |
| `walk_rho1` | Lag-1 autocorrelation of cost along a 2000-step random walk in the swap neighborhood. Near 1 = smooth; lower = rugged. Empty if walk variance is 0. |
| `corr_len_over_n` | Autocorrelation length `ℓ = −1/ln(ρ₁)` divided by n: swaps until fitness decorrelates, as a fraction of n. Measured 0.22–0.33 for every family → ruggedness does not separate easy from hard here; a kick of ≈0.25·n swaps escapes a basin (good default for hot-island mutation / SA restart kicks). Empty when ρ₁ ∉ (0,1). |

### How to use it for solver presets

Class signature per instance = (`dom_*`, `sparsity_*`, `rand_gap_pct`):
- tai-a-like (dom ≈ 60, gap shrinking with n): maximize diversity — larger archive
  d_min, rare migration, hot-heavy island budget.
- Plateau-like (high sparsity, few distinct values: esc, tai-c): sideways-move
  tolerance in SA, permutation-level dedup.
- Structured (tai-b, real-life families; dom > 150, gap > 50%): colder/deeper SA,
  earlier convergence acceptable. Do not tune global parameters on these.
- lipa-a (gap ≤ 7%): pass/fail stress test; exclude from parameter tuning.
