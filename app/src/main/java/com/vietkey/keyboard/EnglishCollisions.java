package com.vietkey.keyboard;

import java.util.HashSet;

/**
 * Từ tiếng Anh mà khi gõ Telex tình cờ ra một âm tiết tiếng Việt hợp lệ (is -> ì, as -> á, six -> sĩ, box -> bõ...).
 * Tạo từ từ điển en_US. Chỉ dùng để quyết định khi đang gõ giữa câu tiếng Anh: từ KHÔNG nằm trong danh sách này
 * (lại, nhé, được...) luôn giữ tiếng Việt, dù các từ đứng trước là tiếng Anh.
 */
final class EnglishCollisions {
    private EnglishCollisions() {}

    private static final String DATA = ""
            + "aa aas acs afar afars afrits afts aims ains air airs airts ais aits ajar ajars ams ans ar archs "
            + "arcs arm arms ars arsing arson arts arum arums asap asarum asarums asp astrs aswarm aswarms ats aux "
            + "auxs aw away aways awn awns aws ax axis axon axons axs baa baas bachs bairn bairns baits bangs bans "
            + "baps bar barf barfs barics barits barm barms barn barns baron barong barongs barons bars basic "
            + "basin bast bats bays bee beech beechs beef beefs been beens beep beeps beer beers bees beet beets "
            + "berefts berets berm berms bes beset besom besot best bets bias bins birchs bis bits boar boars "
            + "boarts boas boast boats bongs boo boom booms boon boong boongs boons boor boors boos boost boot "
            + "boots bops bor bora boras borax boraxs bore bores borics boring borings born borns boron borons "
            + "bors borts borzoi borzois bosom boson bots bow bowfin bowfins bows box boxer boxers boxing boxings "
            + "boxroom boxrooms boxs bumf bumfs bums bungs buns bur buran burans burin burins burn burns burps "
            + "burs bursa bursar bursars burst bury burys bus busing bust busy buts buys bys cains cairn cairns "
            + "cams cans caps car carats caraway caraways caring carings carom caroms carps cars carts cas casa "
            + "casing cast cats caus caw caws cays cens cere ceres cereus cero ceros certs chains chair chairs "
            + "chams chaos chaps char charm charms chars charts chary charys chas chasm chats chaw chaws cheep "
            + "cheeps cheer cheers chef chefs chems cherts chest chics chins chips chirm chirms chirps chis chits "
            + "chocs choir choirs chons chops chore chores chorics chose chosen chow chows chufa chufas chums "
            + "churchs churn churns cir cirs cis cist cits coachs coast coats coax coaxing coaxings coaxs coif "
            + "coifs coins coir coirs coms congs cons coo coom cooms coon coons coop coops coos coot coots cops "
            + "cor core cores corf corfs corm corms corn corns corps cors corsair corsairs corse corset cos cosec "
            + "coset cost cots cow cows cox coxa coxas coxs cums cups cur curchs curs curts cus cusp cuts cw cwm "
            + "cwms cws cwt cwts cyst dafts dais dams dangs daps darics daring darings darn darns darts dats daw "
            + "dawn dawns daws days dd dding ddings dds deem deems deep deeps deer deers def deform deforms defs "
            + "defts dens deps der ders des deserts diams dims dings dins dips dir dirs dirts dis disc dist dits "
            + "docs doer doers does doings doits doms dongs dons doom dooms door doors dor dora doras dorm dorms "
            + "dorps dors dorser dorsers dos dose dost dots dow down downs dows dungs duns dups during durings "
            + "durst dust dux duxs dwarf dwarfs dwt dwts efts emf emfs ems engr engrs engs ens eons er ere eres "
            + "ers erst erts esp ets ex exchs excs execs exerts exon exons exps exr exrs exs exserts exts gains "
            + "gaits gams gangs gans gaps gar gars gas gasp gats gaur gaurs gawp gawps gays gee gees gems geoms "
            + "germ germs gets ghats ghee ghees ghost gies gifts gins giro giros girts gis gist gits goas goats "
            + "goer goers goes gofer gofers goings gongs goo goof goofs goon goons goop goops goos gore gores "
            + "gorps gorse gos gots gown gowns guans guar guars gums guns gust guts guys gyms gyps haaf haafs haar "
            + "haars hafiz hafizs hafts hair hairs haj hajs hams hangs haps harm harms harps harts has hasp hast "
            + "hats haw haws hays hefts hems hens heps here heres herm herms hern herns hero heron herons heros "
            + "herps hertz hertzs hes hex hexs hics hims hins hips his hist hits hoar hoars hoary hoarys hoax "
            + "hoaxs hocs hoer hoers hoes hoist hongs hons hoo hooch hoochs hoof hoofs hoop hoops hoos hoot hoots "
            + "hops hor hora horas horn horns hors horse horst horts hos hose hosp host hots how hows hums hungs "
            + "huns hurst hurts huts hwan hwans hyps if ifs ins ism its ix ixs kafir kafirs kaif kaifs kains kaons "
            + "karats karst karts kas kats keef keefs keen keens keep keeps kef kefs kens keps kerf kerfs kern "
            + "kerns kero keros kex kexs khans kharif kharifs khats kiangs kif kifs kings kins kips kirsch kis "
            + "kist kits koans koas kops kor kors kos kw kws lacs laics lains lair lairs lams langs laps lar "
            + "larchs lari laris lars larum larums larynx larynxs las last lats law lawn lawns laws lax laxs lays "
            + "lechs lee leech leechs leer leers lees leet leets lefts lens lets lex lexs liar liars lifts lings "
            + "lins lips lira liras lis lisp list lits lix lixs loachs loaf loafs loams loans lofts loins longs "
            + "loo loom looms loon loons loop loops loos loot loots lops loran lorans lore lores loris lorn lorns "
            + "los lose loser losers losing lost lots low lows lox loxs lungs lur lurchs lurs lust lux luxs lynx "
            + "lynxs machs macs maims mains major majors mams mans maps mar mara maras marchs marcs mars marts mas "
            + "masc mason mast mats mauns maw maws max maxi maxim maxims maxis maxs mays mayst mechs meet meets "
            + "mems mens mere meres mes meson mets mics mims mins minx minxs mir mirs mirza mirzas mis misc mist "
            + "mix mixs moans moas moats mois moist mojo mojos moms mongs mons moo mooch moochs moon moons moor "
            + "mooring moorings moors moos moot moots mops mora moras moray morays more mores morn morns moron "
            + "morons morse morts mos mosaic most mots mow mows moxa moxas muchs mums mungs muns mus music musing "
            + "must muts mys naan naans naif naifs naos naps narcs naris nary narys nauts nays nee nees neons nes "
            + "nest nets nexts nims nips nits nix nixs noes noms nons noon noons nor norm norms nors nos nose nots "
            + "now nows nowt nowts nuns nursing nus nuts oaf oafs oar oars oast oats ochs of ofay ofays ofs ofts "
            + "ois oms ons oo oof oofs oops oos ops or ora orang orangs oras orchs orcs ore ores ors orts orzo "
            + "orzos os ow own owns ows ox oxs pharos pharynx pharynxs phasic phats pheons phis phons phos phots "
            + "phys quais quarts quartz quartzs quas quasar quasars quasi quays queen queens queer queers quern "
            + "querns ques quest quins quips quirts quits quoins quoits quos quots rafts rains raj rajs rams rangs "
            + "rans raps rasp rats raw raws rays recs reef reefs reest ref refer refers reform reforms refs rems "
            + "reps res rest rias richs rifts rims rings rips rits roachs roams roans roar roaring roarings roars "
            + "roast rocs roes roms roo roof roofing roofings roofs room rooms roos roost root roots rosary "
            + "rosarys rose rosin rots row rowing rowings rows ruins rums rungs runs rust ruts sacs sains sangs "
            + "sans saps saran sarans sari saris sarong sarongs saros sasin sats saw saws sax saxs says sechs secs "
            + "see seem seems seen seens seep seeps seer seers sees sems sere seres serf serfs sets sex sexs sexts "
            + "sics sifts sims sings sinhs sins sips sir sirs sis sits six sixs soaps soar soaring soarings soars "
            + "socs sofa sofar sofars sofas softs soms songs sons soon soons soot soots sops sora soras sore sores "
            + "sori soris sorn sorns sorts sos sots sow sown sowns sows suchs suits sums sungs suns sups sura "
            + "suras surf surfing surfings surfs swam swams swan swans swap swaps swarm swarms swarts swat swats "
            + "swifts swim swims swing swings sworn sworns swot swots swum swums swung swungs syms syns syst tachs "
            + "tains tams tangs tanhs tans taps tar tarn tarns taro taros tarots tarps tars tarsi tarts tas tats "
            + "taus tauts taw taws tax taxa taxas taxi taxing taxings taxis taxon taxons taxs techs tee teem teems "
            + "teen teens tees tens terf terfs term terms tern terns terse test texts thans thar thars thats thaw "
            + "thaws thee thees thefts thems thens there theres therm therms thes these things thins thirst this "
            + "thongs thorax thoraxs thorn thorns thoron thorons thorps thos those thuja thujas thus thuyas "
            + "thwarts thys tics tings tins tips tis tits toast toes tofts toms tongs tons too toos toot toots "
            + "tops tor torchs tore tores tori toris torn torns tors torse torso torts tos tots tow town towns "
            + "tows tox toxics toxin toxins toxs trains traits trams trans transf transfs traps trays tree treen "
            + "treens trees tres trets trims trips troats trons troop troops trots trow trows truism trust trys "
            + "tryst tufa tufas tufts tuis tums tuns tureen tureens turf turfs turfy turfys turn turns turps tuts "
            + "tux tuxs tuyere tuyeres twang twangs twas twat twats twin twins twist twit twits twixts twp twps "
            + "typs ums ups urics urn urns uts vacs vains vair vairs vangs vans var vara varas varix varixs vars "
            + "varus vary varys vas vasa vast vats veep veeps veer veers verse verso verst verts vest vets vex "
            + "vexs vias vims virus vis visa vocs voes vos vow vows xerox xeroxs xis xix xixs xor xors xus yr yrs ";

    private static final HashSet<String> SET = new HashSet<>();

    static {
        for (String w : DATA.trim().split(" ")) SET.add(w);
    }

    static boolean contains(String rawLower) {
        return SET.contains(rawLower);
    }
}
