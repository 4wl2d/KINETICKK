<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Asset provenance

This record covers assets stored in source revision `3abbfea`, which declares
KINETICKK version `0.1.0`, or retained in its reachable Git history.

| Asset | Creator / source | Added | SHA-256 | Rights status |
|---|---|---:|---|---|
| `docs/assets/kinetickk.png` | Screenshot generated from KINETICKK by Vladislav Tomilov (`@4wl2d`) | 2026-07-15 | `416e32d6d8657feffe51185f5c6e63513de490dd3add786c2720aa9e58f23c7a` | GPL-3.0-or-later; see sidecar |
| `docs/assets/kinetic-void.jpg` (removed, retained in Git history) | Screenshot generated from the former Kinetic Void build by Vladislav Tomilov (`@4wl2d`) | 2026-07-15 | `c40ca51fe652524e04f2186c5a0acf0525045b7b44e47a689f1a0e8bc274d951` | Relicensed under GPL-3.0-or-later by the current NOTICE |

The Android launcher icon was added on 2026-09-06. Its editable XML was generated
with Codex from the project's Core and singularity geometry and palette, without
external artwork. These project-authored resources use GPL-3.0-or-later:

| Source | SHA-256 |
|---|---|
| `app/android/src/main/res/drawable/ic_launcher_foreground.xml` | `8f29fcfdc4a0bb164119ba5d52b4b1b94ffebb513af47b2561197e4af1b6455a` |
| `app/android/src/main/res/mipmap-anydpi/ic_launcher.xml` | `9bd8ccfedb57bab70c2f6c373314018d1cc094626b00236c58421bd608a49440` |
| `app/android/src/main/res/values/colors.xml` | `5a79b48550bb79d73fcba14e57073eade497392cebede004603586a64a47612b` |

The game currently stores no external music, sound-effect, model, or texture
files. Audio is synthesized at runtime by project code and visuals are drawn by
project code. The interface bundles third-party fonts under the SIL Open Font
License 1.1 as Compose resources; `THIRD_PARTY_NOTICES.md` lists every bundled
font. The redesign fonts were added on 2026-09-27 as modified static, subset
instances of the upstream Google Fonts families:

| Asset | Creator / source | Added | SHA-256 | Rights status |
|---|---|---:|---|---|
| `foundation/design/src/commonMain/composeResources/font/kk_wide_black.ttf` | Unbounded 900; The Unbounded Project Authors, from github.com/google/fonts `ofl/unbounded`; static instance + subset via fontTools | 2026-09-27 | `d192d80e2f122f694598236c103c455402198bb3d2e958132aecd6e307b7b268` | SIL OFL 1.1; notice in `composeResources/files/unbounded-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_wide_bold.ttf` | Unbounded 700; The Unbounded Project Authors, from github.com/google/fonts `ofl/unbounded`; static instance + subset via fontTools | 2026-09-27 | `fdc95ee8b284c30c4d736f3e810f7a3ddcde4f3d4634901fed5c214c1bb2afbe` | SIL OFL 1.1; notice in `composeResources/files/unbounded-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_cond_black_italic.ttf` | Sofia Sans Extra Condensed 900 italic; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasansextracondensed`; static instance + subset via fontTools | 2026-09-27 | `f6c8118cc5ee520d4af123ef3d46e42372f97c0e2d9fa771633fa886e36cde93` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-extra-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_cond_black.ttf` | Sofia Sans Extra Condensed 900; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasansextracondensed`; static instance + subset via fontTools | 2026-09-27 | `15811068038c552ea73f246c462bd91fdf148dfb10204a9ec242f1e42f5dc132` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-extra-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_cond_extrabold.ttf` | Sofia Sans Extra Condensed 800; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasansextracondensed`; static instance + subset via fontTools | 2026-09-27 | `e5478a82779155eae4d61515a6bf3fae4e8b48fd49189972280e989d0dba5690` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-extra-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_body_regular.ttf` | Sofia Sans Semi Condensed 400; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasanssemicondensed`; static instance + subset via fontTools | 2026-09-27 | `da1e3fa4f5a7dc0ac24e9691934084743d02da76d5a41e06cfd2e86dd389773b` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-semi-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_body_medium.ttf` | Sofia Sans Semi Condensed 500; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasanssemicondensed`; static instance + subset via fontTools | 2026-09-27 | `bd739b6d7da875711f37715b8a4991bc5d89aeb0c9c560bd3d1d6ea19bcb8c1a` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-semi-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_body_bold.ttf` | Sofia Sans Semi Condensed 700; The Sofia Sans Project Authors, from github.com/google/fonts `ofl/sofiasanssemicondensed`; static instance + subset via fontTools | 2026-09-27 | `1c6951e71c3a9e3030e99e2155aaa1b8a79918af789a5e8bda923d08b5353103` | SIL OFL 1.1; notice in `composeResources/files/sofia-sans-semi-condensed-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_mono_medium.ttf` | Martian Mono 500; The Martian Mono Project Authors, from github.com/google/fonts `ofl/martianmono`; static instance + subset via fontTools | 2026-09-27 | `6d3df948980564679577bd1f75ea437f8bc55fe2ef5fbd541c5e5721cff993c9` | SIL OFL 1.1; notice in `composeResources/files/martian-mono-OFL.txt` |
| `foundation/design/src/commonMain/composeResources/font/kk_mono_bold.ttf` | Martian Mono 700; The Martian Mono Project Authors, from github.com/google/fonts `ofl/martianmono`; static instance + subset via fontTools | 2026-09-27 | `f5db3d0dc1e8fc295f439ddd80eab15b709de026e860de26ea76c5dcb36306f6` | SIL OFL 1.1; notice in `composeResources/files/martian-mono-OFL.txt` |

The preferred source for a screenshot is the corresponding game source and the
steps needed to reproduce the screen. Keep editable source files for future art,
audio, and store assets whenever those files exist.

For every future asset, record its path, creator, creation or acquisition date,
source, hash, license, and any signed assignment or purchase receipt before the
asset is committed. Do not add an asset whose chain of rights is unclear.
