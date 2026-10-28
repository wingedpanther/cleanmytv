# Known devices

A per-model quick-reference of the debloat lists bundled with the app, generated from
`resources/upstream/examples/*/debloat.txt` headers — the authoritative detail lives in each
list file. Use it to look your TV up before connecting; the app itself picks the matching
list automatically during **Clean up**, so an unlisted model still gets a sensible starting
point (the packages other TVs disable, or the family list for Xiaomi/Mi and TCL/Thomson).

The lists come from upstream cleanyourtv. The launcher the app installs is CleanMyTV Home, which
needs Android 9 or newer. On a TV in the OS column below 9, use the lists for debloat only.

| Model | OS | Stock launcher | Notes |
|---|---|---|---|
| AiPro2K (Expressluck, device dongnae), Android TV 14, firmware UKN6.260116.085 | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| AIWA AI PONT SA (Changhong, brand AIWA, device osaka, product ISDB86AW) | 14 | `com.google.android.apps.tv.launcherx` | - |
| Amazon Fire TV Stick 4K, 1st gen 2018 (AFTMM, codename mantis), Fire OS 6.7... | 7.1 | - | needs pairing; stock launcher is protected — debloat only |
| Amazon Fire TV Stick Lite, 1st gen 2020 (AFTSS, codename sheldon), Fire OS ... | 9 | - | stock launcher is protected — debloat only |
| BenQ E55-735 (55" 4K, Taiwan), Google TV 14, firmware UKNV.260506.001 | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| BenQ E55-750 (55" 4K, Taiwan), Google TV on Android 11 (SDK 30), firmware R... | 11 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home |
| BYINTEK T5 Max projector, built-in Google TV stick: ro.product.model="4K Go... | 14 | - | needs pairing; setupwraith grabs Home |
| CHiQ "AI PONT" (DVB93CQ, built by Changhong, codename samseong), Android TV... | 11 | `com.google.android.tvlauncher` | needs pairing |
| CHiQ "AI PONT" (Changhong, product GTVU65, device dangni), Google TV 14 (AP... | 11 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| FFalcon (雷鸟) 鹤6 PRO 65S585C Pro (China), device tcl_mt5879_cn, MediaTek MT9... | 11 | `com.tcl.cyberui` | needs pairing |
| Freebox Player POP (Free, France; codename fbx8am, Amlogic, 2 GB RAM), Andr... | 10 | - | setupwraith grabs Home; streaming box (no inputs) |
| Chromecast with Google TV 4K (2020, device sabrina, product sabrina_prod_st... | 4 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| Haier Android TV DVB2K (ro.product.model, device broadway, product HaierATV... | 11 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home |
| Haier H32S80EFX (ro.product.model "Haier MatrixTV CE", product HRT56EU, dev... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| Hisense E5Q Pro (internal model "VIDAA_TV", MediaTek MT9653), VIDAA on Andr... | 11 | - | - |
| Hisense HG9 (HiSmart 4K A4FFM, device laohushan, MediaTek), Android TV 9 (A... | 9 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Hisense HiSmartTV A4 (device xiaoyushan, product xiaoyushan-em, MStar/Media... | 9 | `com.google.android.tvlauncher` | - |
| Hisense HiSmartTV A4 (device xiaoyushan, MStar/MediaTek m7322), Android TV ... | 11 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home |
| Hisense SmartTV 4K FFM (device huangshan, MediaTek), Android TV 10, firmwar... | 10 | `com.google.android.tvlauncher` | - |
| Hisense U75 (device hengshan, MediaTek), Google TV / Android 12, build STT2... | 12 | `com.google.android.apps.tv.launcherx` | needs pairing |
| Hitachi 65QLEDSM20 (built by MTC, codename bangbae, MediaTek m7322), Androi... | 11 | `com.google.android.tvlauncher` | needs pairing |
| Hitachi CDH-LE32SMART21 (32", Latin America / Argentina; TCL-built: device ... | 11 | `com.google.android.tvlauncher` | setupwraith grabs Home; dedicated Live TV app |
| Insignia 55" Class F30 4K Fire TV (NS-55F301NA25, model AFTDCT31, codename ... | 9 | - | stock launcher is protected — debloat only; dedicated Live TV app |
| JMGO N1S projector (model K80A, codename holan, MediaTek m7332, about 1.8 G... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| Kalley R10G (device R10G, product ACER_FFM, made by MOKA), Google TV on And... | 14 | `com.google.android.apps.tv.launcherx` | - |
| MTC UK 2K Android TV (codename tabata), Android TV 11, firmware RTO8.250807... | 11 | `is` | - |
| NVIDIA SHIELD TV 2019 tube (model P3430, codename sif, Tegra X1+, 2 GB RAM)... | 11 | `is` | setupwraith grabs Home; streaming box (no inputs) |
| NVIDIA SHIELD TV Pro (2019, model P2897, codename mdarcy, Tegra X1+, 3 GB R... | 11 | `is` | setupwraith grabs Home; streaming box (no inputs) |
| OnePlus TV (model D00, codename Dubai), Google TV / Android 14, firmware UT... | 14 | `com.google.android.tvlauncher.` | dedicated Live TV app |
| onn 4K Streaming Box (2023, Walmart; device YOC, product onn_4k_gtv, Amlogi... | 14 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| Panasonic 4KTV-0SU (Skyworth-built, device SW4H, product SW4H_PANATV, Media... | 10 | `is` | setupwraith grabs Home; dedicated Live TV app |
| Panasonic 4KTV-JUP (Jupiter_PA, MediaTek), Android TV 11 (API 30), firmware... | 11 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Panasonic TH-40HS550K (Skyworth-built, GS655, codename shibuya, MediaTek m5... | 11 | `is` | setupwraith grabs Home; dedicated Live TV app |
| Philips 50PUS8517/12 (50" 4K UHD LED, 3-sided Ambilight, P5 Picture Engine,... | 11 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Philips 55OLED707/12 (55" 4K OLED with Ambilight), UK | 11 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Philips 55PUS7503/12 (TP Vision TPM171E, device PH7M_EU_5596), Europe, 2018... | 8.0 | `com.google.android.tvlauncher` | needs pairing; dedicated Live TV app |
| Philips 65PUS8102/12 (TP Vision TPM171E, device PH7M_EU_5596), Europe, 2017... | 8.0 | `com.google.android.tvlauncher` | needs pairing; dedicated Live TV app |
| Philips R8G Google TV (built by MOKA/TPV, device whitefield, Realtek RTD285... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Philips Smart 4K TV (India), Google TV 14, firmware UKN5.251016.083 | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; dedicated Live TV app |
| Philips TPM191E (TP Vision, device PH9M_EA_5599, MediaTek mt5887), Europe | 12 | `com.google.android.tvlauncher` | - |
| Portronics Beem 470 Smart LED Projector (Allwinner H713, sun50iw12p1), Andr... | 11 | `is` | - |
| PRISM+ Q65 Ultra 2026 QLED 4K AI Google TV (device R10G, product PRISMPLUS_... | 14 | `com.google.android.apps.tv.launcherx` | setupwraith grabs Home |
| RCA AI PONT SA (Changhong, device redwood, product ISDB92RC) | 14 | `com.google.android.tvlauncher` | - |
| Redmi TV (MiTV-ANTL0, codename starwars), Android 5.1.1 (SDK 22), firmware ... | 5.1 | - | - |
| SANYO ATV R2 (manufacturer SCBC, device R2, Realtek RTD285O), Android TV 9 ... | 9 | `is` | - |
| Skyworth SWTV-21AE ("Living Room TV"), Android TV 14, firmware UKNV.260824.001 | 14 | `com.google.android.tvlauncher` | needs pairing |
| Skyworth SWTV-24NA-4K NF (device eleonas, product SW10_NA_NF, MediaTek mt58... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Sony BRAVIA 4K GB (EU: device BRAVIA_ATV2_EU, product BRAVIA_ATV2). Android... | 9 | `com.google.android.tvlauncher` | - |
| Sony BRAVIA 4K GB (BRAVIA_ATV2_UC, MediaTek mt5891), US/Canada firmware | 9 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; set live_tv_package empty |
| Sony BRAVIA 4K UR2 (device BRAVIA_UR2_4K, MediaTek) | 10 | `com.google.android.tvlauncher` | - |
| Sony BRAVIA 4K VH2 (device BRAVIA_VH2, KD-55X80J, MediaTek), EU firmware | 12 | `com.google.android.apps.tv.launcherx` | - |
| Sony BRAVIA 4K VH21 (KD-65X85K, 2022, device BRAVIA_VH21, MediaTek MT5895),... | 12 | `com.google.android.apps.tv.launcherx` | setupwraith grabs Home |
| Sony BRAVIA 9 II, K-65XR90M2 (2026, device BRAVIA_AE3, product BRAVIA_AE3_P... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| Sony BRAVIA 4K AE2 (BRAVIA_AE2, MediaTek MT5897 "merak"), North American fi... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; port 5555 closes on reboot |
| Sony BRAVIA BF1 (BRAVIA 2 II class, device BRAVIA_BF1_4K_A, MediaTek, 2.4 G... | 12 | `com.google.android.apps.tv.launcherx` | - |
| Sony BRAVIA TL2 (device OD0M_EA_T32, built by TPV, MediaTek, ~1.7 GB RAM), ... | 11 | `com.google.android.tvlauncher` | setupwraith grabs Home; dedicated Live TV app |
| Sony BRAVIA VU31 4K (device BRAVIA_VU31_4K, Realtek SoC), India firmware, 2... | 14 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| Sony BRAVIA KD-55X8509C (2015, "BRAVIA 4K 2015", device SVP-DTV15, MediaTek... | 7.0 | `com.google.android.leanbacklauncher` | needs pairing |
| Sony BRAVIA KD-55X85J (2021, device BRAVIA_VH2, MediaTek MT5895), South Ame... | 12 | `com.google.android.apps.tv.launcherx` | setupwraith grabs Home |
| Sony BRAVIA KD-55XE8596 (2017, model string "BRAVIA 4K GB", product BRAVIA_... | 9 | `com.google.android.tvlauncher` | needs pairing |
| Sony BRAVIA KD-65X80J (2021, platform BRAVIA_VH2, MediaTek MT5895), Google ... | 10 | `com.google.android.apps.tv.launcherx` | setupwraith grabs Home |
| Sony BRAVIA KD-65X9300D (2016, SVP-DTV15 "BRAVIA 4K 2015" platform, MediaTe... | 8.0 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home |
| Sony BRAVIA XBR-65X900F (2018, BRAVIA_ATV3_4K, MediaTek MT5891), US/Canada ... | 9 | `com.google.android.tvlauncher` | needs pairing |
| Sony BRAVIA XBR-65X900E (2017, BRAVIA_ATV2, MediaTek), US/Canada firmware | 9 | `com.google.android.tvlauncher` | needs pairing |
| Stellamore T950S projector (Droidlogic be30a3, ro.product.devicetype=T950S,... | 13 | - | needs pairing |
| TCL 32S5400A (2023, 32" FHD), unit with SonyLIV/YuppTV preloads: device C06... | 11 | `com.google.android.tvlauncher` | needs pairing; dedicated Live TV app |
| TCL 40S5400A (40" FHD, S5400 series), EU unit: device C06, product C06_2K_G... | 11 | `com.google.android.tvlauncher` | dedicated Live TV app |
| TCL 43C645 (reports as "Smart TV Pro"), Android TV 11 (SDK 30), Realtek RTD... | 11 | `is` | needs pairing; setupwraith grabs Home |
| TCL 43EP660, Europe (Croatia) (ro.product.model BeyondTV, board rtd285o, so... | 9 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| TCL 50EP680 (Europe), `ro.product.model` BeyondTV, brand TCL, board `rtd285... | 11 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| TCL 55C815 (EU/Denmark, 2020), `ro.product.model` BeyondTV2, device BeyondT... | 11 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| TCL 55S470G (S4 class Google TV, US; device G10, product G10_4K_US_NF, Real... | 12 | `com.google.android.apps.tv.launcherx.` | needs pairing |
| TCL 65C725 (reports as "Smart TV Pro", device BeyondTV4), Android TV 11 (SD... | 11 | `com.google.android.tvlauncher` | needs pairing; dedicated Live TV app |
| TCL 65C735 (reports as "Smart TV Pro", device G05, product G05_4K_GB), Goog... | 11 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home |
| TCL ak30a5 — Chinese-market TCL Android TV 9, Amlogic SoC, 1.5 GB RAM | 9 | `com.tcl.cyberui` | - |
| TCL BeyondTV (ro.product.model BeyondTV, TCL), Android TV 9 (API 28), build... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| TCL BeyondTV, EU/Nordic unit (ro.product.model BeyondTV, board RTD2851, cli... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| TCL BeyondTV2, Latin America / Brazil market (ro.product.model BeyondTV2, b... | 9 | `com.google.android.tvlauncher` | needs pairing; dedicated Live TV app |
| TCL 75" C755 QD-Mini LED Google TV, 2024, Australia (device G08, model "Sma... | 14 | `com.google.android.apps.tv.launcherx.` | needs pairing |
| TCL Smart TV Pro, US (device G05, product G05_4K_US, board C05, MediaTek MT... | 11 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| TCL Smart TV (reports as "Smart TV"; device G07, product G07_4K_GB_NF, Real... | 11 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home |
| TCL 55" 4K Google TV, 2023 model year, US (device G10, product G10_4K_US_NF... | 12 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| TCL 55Q10G Pro (China), device tcl_mt5879_cn / product tcl_mt5879_cn_db, Me... | 11 | `com.tcl.cyberui` | - |
| TCL QM6K Pro Google TV, 2025, US (device G08, model "Smart TV Pro", MediaTe... | 14 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home |
| TCL UnionTV, Latin America (ro.product.model UnionTV, TCL; persist.tcl.clie... | 9 | `com.google.android.tvlauncher` | setupwraith grabs Home; dedicated Live TV app |
| Thomson 32HE5800 (TCL-built: ro.product.model UnionTV, persist.tcl.client_t... | 9 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Toshiba Fire TV (AFTHA004, codename hazel), Fire OS 7 / Android 9 (SDK 28),... | 9 | - | stock launcher is protected — debloat only |
| Vidda VIDAA_TV (Hisense sub-brand; device MT9653, board mt5879), Android TV... | 11 | `com.jamdeo.tv.vod` | needs pairing |
| Vu 4K (ro.product.model "VU 4K", device lushan, manufacturer HVCL, MediaTek... | 12 | `com.google.android.apps.tv.launcherx` | needs pairing; dedicated Live TV app |
| Vu Premium FHD 43-inch (ro.product.model Smart 2K ATV4, device hamamatsucho... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Wanbo X5 Pro projector, built-in Google TV stick: ro.product.model="4K Goog... | 14 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| XGIMI Horizon S Pro projector (model XK03H, device xgimigalileo, MediaTek),... | 11 | `com.google.android.tvlauncher` | setupwraith grabs Home; streaming box (no inputs) |
| Xiaomi Mi Box S (model MIBOX4, MDZ-22-AB, codename oneday, Amlogic gxl), An... | 9 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| Xiaomi Mi Box S 2nd gen (model MiTV-AFKR0, codename jaws, Amlogic), Google ... | 11 | `com.google.android.apps.tv.launcherx.` | needs pairing; setupwraith grabs Home; streaming box (no inputs) |
| Xiaomi Mi TV 4A (MiTV-MOOQ1, codename tarzan), Android TV 10, firmware QTG3... | 10 | - | - |
| Xiaomi MiTV-AESP0 (codename aquaman), Android TV 9 (SDK 28), firmware PI.2055. | 9 | `com.google.android.tvlauncher` | streaming box (no inputs); dedicated Live TV app |
| Xiaomi TV Box S 3rd gen (MiTV-AFMU0, codename twilight, Amlogic), Google TV... | 14 | `gone` | needs pairing; setupwraith grabs Home |
| Xiaomi MiTV-ASTP1 (codename maverick; China-market firmware), Android 9 (SD... | 9 | `com.mitv.tvhome` | - |
| Xiaomi MiTV-AXFR1 (codename rango, product dune; Amlogic/droidlogic, 1 GB R... | 11 | `com.google.android.tvlauncher` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Xiaomi MiTV-AXSO0 (codename dangal; sold as Mi TV 4C Pro 2019 and Mi TV 4A ... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Xiaomi MiTV-AXSO2 (codename dangalUHD, Amlogic/droidlogic, 2 GB RAM), Andro... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Xiaomi Mi TV (MiTV-MOOQ0, codename croods), Android TV 10, firmware QTG3.20... | 10 | `sits` | needs pairing |
| Xiaomi MiTV-MOOR2 (codename venom), Google TV / Android 11, firmware RTM5.2... | 11 | `com.google.android.apps.tv.launcherx` | needs pairing |
| Xiaomi MiTV-MOSR4 (codename irobot), Google TV / Android 11 (SDK 30), firmw... | 11 | `com.google.android.apps.tv.launcherx` | needs pairing; setupwraith grabs Home; dedicated Live TV app |
| Xiaomi MiTV-MSSP3 (codename machuca), Android TV 11 (SDK 30), firmware RTT2... | 11 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Xiaomi MiTV-MSSP3 (codename machuca), Android TV 9 (SDK 28), firmware PI.75... | 9 | `com.google.android.tvlauncher` | dedicated Live TV app |
| Xiaomi TV A Pro 75" 2026 (MiTV-MZTU0, codename river, MediaTek), Google TV ... | 14 | `com.google.android.apps.tv.launcherx` | dedicated Live TV app |

_107 lists, synced from upstream at rev fd1ee0a94cad (2026-10-24). Regenerate this table when the examples sync._

## Reporting back

Ran a list on a model not mentioned here, or something broke when a package went off?
The upstream project collects device reports (see their CONTRIBUTING for the format). A
report that survives a few days of living with the TV is what promotes an inferred family
match into a dedicated list — this table then grows by one row on the next sync.
