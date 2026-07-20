# JavaIslandModelQAPSolver

An Island-Model GA + SA hybrid solver for the Quadratic Assignment Problem,
written in Java (in design phase — no code yet). Includes a mirror of the QAPLIB
benchmark set (Burkard, Karisch & Rendl; later maintained by Hahn & Anjos),
obtained from the University of Edinburgh DataShare deposit
[DS_10283_4390](https://datashare.ed.ac.uk/handle/10283/4390), plus the dataset
characterization driving the solver design.

## Contents

- `DS_10283_4390/qapdata/` — 136 problem instances (`.dat`: `n`, matrix A, matrix B)
- `DS_10283_4390/qapsoln/` — 128 solution files (`.sln`: `n`, objective value, permutation)
- `DS_10283_4390/README-QAPLIB-Problem Instances and Solutions.pdf` — official
  status document (April 2012 snapshot)
- `qaplib_characteristics.csv` — per-instance characterization (symmetry, sparsity,
  Vollmann–Buffa dominance, tie structure, value ranges, random-solution gap to
  best known, random-walk autocorrelation)

## Objective function

Minimize over permutations `p`:  `cost(p) = Σᵢ Σⱼ A[i][j] · B[p(i)][p(j)]`
(pure quadratic, integer data; largest objective ≈ 1.19e9 — safe in int64/float64).

## Solution status

Per the bundled 2012 README, 104 instances were solved to optimality. Since then
tai35b, tai40b (2015) and tai30a, sko42 (2021) were closed, leaving **28 open**:
sko49–sko100f, tai35a, tai40a, tai50a/b, tai60a/b, tai80a/b, tai100a/b, tai150b,
tai256c, tho40, tho150, wil50, wil100 (best known values, no optimality proof).

## Data quirks (verified computationally)

- `kra32.sln` header says 88900 but its permutation evaluates to the true
  optimum **88700** (header typo; 88900 belongs to kra30a).
- `tai40a.sln` permutation is **0-based**; all other `.sln` files are 1-based.
- Eight `.sln` files store the **inverse** permutation convention
  (esc128, kra30a, kra30b, ste36c, tai60a, tai80a, tho30, tho150): their values
  reproduce under `Σ A[p(i)][p(j)]·B[i][j]`.
- Some `.sln` files use commas rather than spaces in the permutation.
- `esc16f` is degenerate: its flow matrix is all zeros, every permutation costs 0.
- 37 instances are asymmetric (all bur, all lipa, tai-b series); bur26a–h,
  tai64c and tai256c have nonzero diagonals (permutation-dependent linear term —
  do not skip diagonals when evaluating).
- tai10a/tai10b have no `.sln`/README entry; exact optima computed here by
  enumeration: **tai10a = 135028**, **tai10b = 1183760**.
