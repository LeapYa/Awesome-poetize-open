package com.ld.poetry.service.ai.image;

/**
 * 高真实感·通用文章封面提示词模板引擎。
 *
 * <p>核心思路：用极其具体的"物理材质"和"摄影参数"限制 AI 发散，把它死死按在"现实世界"里。
 * 模板分为物品类（object）和人物类（portrait）两套。
 *
 * <p>工作流程：
 * <ol>
 *   <li>{@code global}/{@code dedicated} 模式：LLM 按对应模板的系统提示词直接生成完整英文生图 prompt，
 *       材质/镜头/光影/人物穿搭等全部由 LLM 根据文章内容自动提炼。</li>
 *   <li>{@code plain} 模式：不调用 LLM，使用固定默认值拼接 prompt；
 *       此时会取文章标题/内容作为主体描述，避免完全脱离文章。</li>
 * </ol>
 */
public final class CoverPromptTemplate {

    private CoverPromptTemplate() {}

    // ==================== 物品类预设值（plain 模式默认拼接用） ====================

    /** 物品类 - 材质细节（抗 AI 塑料感的核心） */
    public static final String OBJECT_MATERIAL =
            "matte anti-glare screen finish, PBT keycap grain, subtle wear marks";

    /** 物品类 - 镜头与构图 */
    public static final String OBJECT_LENS =
            "extreme close-up macro shot, top-down angle, shallow depth of field";

    /** 物品类 - 光影与背景环境 */
    public static final String OBJECT_SCENE =
            "Late-night quiet studio background with heavy bokeh, blurred code on screen visible, warm white monitor light bar asymmetric lighting";

    // ==================== 人物类预设值（plain 模式默认拼接用） ====================

    /** 人物类 - 人物设定（年龄性别职业） */
    public static final String PORTRAIT_CHARACTER =
            "a 20-something Asian woman, identity as a focused developer";

    /** 人物类 - 情绪与状态（含表情、眼神、氛围） */
    public static final String PORTRAIT_EMOTION =
            "Looking down intently at a glowing screen, deep focused eyes, immersive hardcore atmosphere";

    /** 人物类 - 穿搭风格 */
    public static final String PORTRAIT_OUTFIT =
            "a minimalist solid-color turtleneck sweater, black-framed glasses";

    /** 人物类 - 摄影视角 */
    public static final String PORTRAIT_CAMERA =
            "85mm prime portrait lens, eye-level close-up, heavy background bokeh";

    /** 人物类 - 场景与光线 */
    public static final String PORTRAIT_SCENE_LIGHT =
            "Minimal modern studio background, cold blue screen reflection vs warm ambient lamp contrast, smooth bokeh";

    // ==================== 模板专用 refine_prompt（指导 LLM 按真实感公式生成完整 prompt） ====================

    /** 物品类 - LLM 系统提示词：按真实感公式生成完整生图 prompt（含材质/镜头/光影，全部由 AI 提炼） */
    public static final String OBJECT_REFINE_PROMPT =
            "你是一个专注于写实摄影级封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【高真实感·通用文章封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张高清真实摄影级图片作为封面。整体风格写实、极具高级感与视觉冲击力。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念最紧密相关的日常具象实体物品。例如：若文章关于AI或软件代码，可以是显示着数据图表/代码的极窄边框显示器、运行中的服务器机柜、或是翻开的学术期刊；若关于生活，可以是咖啡与书本。必须是现实生活中真实存在的普通物品，绝对不要生成抽象数据流、悬浮发光体，也不要强行生成机械臂或科幻机器人等不切实际的元素）。\n" +
            "【材质细节】：细节极其丰富，呈现出真实世界的物理质感。重点保留（具体物品的物理特性，如屏幕的防眩光哑光感、纸张的纤维纹理、键盘的磨砂质感等）。色彩真实自然，拒绝任何强烈的AI霓虹发光与塑料感。\n" +
            "【镜头与构图】：采用（真实的摄影器材和机位，如85mm定焦镜头，微距特写等）。焦点锁定在核心主体上，透视自然，画面极具景深张力。\n" +
            "【光影与背景】：背景为与主体高度匹配的真实场景（例如：深夜安静的工作室、阳光充足的靠窗书桌或整洁的机房），进行平滑且明显的单反级大光圈虚化（Bokeh）。采用（屏幕挂灯的非对称暖光、自然侧光等真实光源），光影过渡柔和自然。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 人物类 - LLM 系统提示词：按真实感公式生成完整生图 prompt（含人物/情绪/穿搭/镜头/光影，全部由 AI 提炼） */
    public static final String PORTRAIT_REFINE_PROMPT =
            "你是一个专注于写实摄影级封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【高真实感·人物类封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【全局画质与风格】：生成一张极高画质真实摄影人像作为封面。要求照片级写实、电影级质感，极具视觉冲击力。拒绝任何强烈的AI滤镜感、过度磨皮与塑料感。\n" +
            "【核心人物特征】：画面的主体是一位（根据文章推断年龄、性别、身份设定，如专注的开发者/都市青年）。\n" +
            "【人物细节与表情】：人物面部五官自然，重点保留真实的皮肤纹理、毛孔与微小的瑕疵（真实感核心）。人物正在（具体动作），眼神（特定状态），传达出（符合文章的氛围感）的情绪。\n" +
            "【服化道】：人物穿着（符合身份的服装风格），搭配（相关配饰细节），服饰材质展现出真实的物理纹理。\n" +
            "【镜头语言】：采用（真实的镜头类型与视角，如85mm定焦平视特写，或35mm半身视角），构图充满张力。\n" +
            "【环境与光影】：背景为真实的（具体生活/工作场景），进行明显的单反级大光圈虚化（Bokeh）。采用（真实世界的光影类型，如冷暖对比光、自然侧光等），光影过渡自然高级。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【全局画质】”、“【核心人物】”等前缀标签，也不要有任何解释说明！";

    /** 毛毡Q版可爱风 - LLM 系统提示词：按羊毛毡手工感Q版风格公式生成完整生图 prompt */
    public static final String FELT_REFINE_PROMPT =
            "你是一个专注于毛毡Q版可爱风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【毛毡Q版可爱风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张毛毡材质的Q版可爱风插画作为封面。整体呈现出手工羊毛毡的蓬松质感与治愈氛围，色彩柔和高级，充满童趣与温暖。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象物体或小动物，并将其Q版化。例如：若文章关于编程，可以是一只戴着小眼镜、抱着迷你笔记本电脑的羊毛毡小熊；若关于旅行，可以是一只背着小书包的羊毛毡小兔子。必须是圆润可爱的形象，绝对不要生成抽象符号或冷冰冰的真实物品）。\n" +
            "【材质细节】：主体与场景全部呈现真实羊毛毡手工质感——可见蓬松的羊毛纤维、细密的针扎痕迹、微微起毛的表面触感。色彩使用低饱和的马卡龙色系（如奶白、薄荷绿、樱花粉、雾霾蓝），温柔不刺眼。可点缀小配饰（如纽扣眼睛、刺绣纹样、棉线缝边）。\n" +
            "【造型与构图】：主体采用Q版比例（头大身小约2-3头身），轮廓圆润饱满，表情夸张可爱（大笑、惊讶、专注等）。采用居中或微微偏上的构图，留出充足留白，画面轻盈不拥挤。可添加与主题相关的小道具（毛毡花朵、星星、爱心等）。\n" +
            "【光影与背景】：背景为纯色或柔和的渐变（如奶油黄到淡粉的过渡），可点缀毛毡质感的小装饰（云朵、彩虹、小草）。光线均匀柔和、无明显阴影，整体明亮温暖，呈现治愈系氛围。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 赛博朋克霓虹风 - LLM 系统提示词：按赛博朋克未来霓虹美学公式生成完整生图 prompt */
    public static final String CYBERPUNK_REFINE_PROMPT =
            "你是一个专注于赛博朋克霓虹风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【赛博朋克霓虹风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张赛博朋克霓虹未来感的图片作为封面。整体风格前卫冷峻、极具科技张力与赛博美学，色彩对比强烈，画面充满未来都市的霓虹质感。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象物品或场景，并注入科技改造元素。例如：若文章关于AI，可以是一台悬浮的全息显示器、布满神经接口的服务器塔、或一只机械义眼；若关于生活，可以是一杯冒着霓虹蒸汽的合成饮料。必须是带有未来改造感的实体，绝对不要生成纯抽象光效或纯文字）。\n" +
            "【材质细节】：呈现出赛博工业质感——拉丝金属、碳纤维纹理、亚克力透明壳体、裸露的LED灯带与霓虹光管、电路板纹路。物体表面有细微划痕、油污、水渍等使用痕迹，增强真实感与故事感。色彩以深蓝紫为底，搭配品红、青蓝、电光绿的霓虹高光。\n" +
            "【镜头与构图】：采用低机位仰拍或荷兰角倾斜构图，配合广角镜头张力，让主体显得宏伟压迫。焦点锁定核心主体，背景虚化呈现光斑与霓虹流光，画面极具景深与戏剧性。\n" +
            "【光影与背景】：背景为雨夜的赛博都市——湿润的街道反射霓虹光斑、远处高耸的全息广告牌、飘散的蒸汽与雨丝。主光为品红与青蓝的霓虹冷光对比，辅以暖橙色街灯点缀，光影锐利、对比强烈，呈现典型的赛博朋克夜景氛围。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 水彩手绘风 - LLM 系统提示词：按水彩插画美学公式生成完整生图 prompt */
    public static final String WATERCOLOR_REFINE_PROMPT =
            "你是一个专注于水彩手绘风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【水彩手绘风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一幅水彩手绘插画风格的图片作为封面。整体呈现出通透灵动的水彩质感——色彩温润、笔触自然、意境清新，充满文艺气息与手绘温度。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象事物，用水彩意趣表达。例如：若文章关于旅行，可以是一座小镇教堂、一只复古行李箱；若关于生活，可以是一杯咖啡与一本翻开的书；若关于自然，可以是一束野花。必须是富有生活气息的实体，绝对不要生成纯抽象符号）。\n" +
            "【色彩与笔触】：使用透明水彩颜料质感——色彩叠加处可见水痕与晕染，边缘柔和有毛边。颜色清新柔和，以暖色调或莫兰迪色系为主，整体色调统一和谐。可见水彩笔触的自然痕迹、颜料颗粒感、纸面纹理。留白处有自然的水渍边缘。\n" +
            "【构图与层次】：采用清新简洁的构图——主体突出，画面有呼吸感。可加入与主题相关的小元素点缀（叶片、花朵、几何图形），形成前中后景层次。构图轻盈不拥挤，有手绘插画的装饰感。\n" +
            "【背景与氛围】：背景以大面积留白或淡色晕染为主，可点缀零星的水彩斑点、色块或纹理。整体氛围温暖治愈、文艺清新，仿佛一页手绘本中的插画，充满生活的小确幸。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 国风水墨画 - LLM 系统提示词：按中国传统水墨写意美学公式生成完整生图 prompt */
    public static final String INK_REFINE_PROMPT =
            "你是一个专注于国风水墨画封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【国风水墨画·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一幅中国传统水墨画风格的图片作为封面。整体呈现出东方水墨写意美学——意境悠远、气韵生动、留白雅致，具有文人画的诗意与禅意。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象事物，并以水墨意象表达。例如：若文章关于哲理，可以是远山孤松、一叶扁舟；若关于文化，可以是一支毛笔、一卷古书；若关于生活，可以是一盏清茶、一枝梅花。必须是具有东方文化意象的实体，绝对不要生成现代科技物品或纯抽象符号）。\n" +
            "【笔墨细节】：使用中国传统笔墨技法——主体用浓墨勾勒轮廓与结构，墨色有枯湿浓淡的层次变化（焦墨、浓墨、重墨、淡墨、清墨五色）。可见毛笔笔触的飞白、晕染、皴擦纹理。设色淡雅，以水墨为主，可点缀少量赭石、花青、藤黄等传统矿物颜料，整体色调温润古朴。\n" +
            "【构图与章法】：采用传统中国画构图——讲究留白与虚实相生，主体偏居一侧或一隅，留出大片空白营造意境。可运用散点透视或高远/深远/平远三远法。画面构图疏朗雅致，气韵流动，不拥挤不堆砌。\n" +
            "【意境与背景】：背景大量留白，或以淡墨晕染出远山、云雾、江水等虚境，营造空灵悠远的意境。整体氛围宁静淡然，具有文人雅士的书卷气与禅意，仿佛一首无声的诗。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 像素复古风 - LLM 系统提示词：按 8-bit/16-bit 像素艺术美学公式生成完整生图 prompt */
    public static final String PIXEL_REFINE_PROMPT =
            "你是一个专注于像素复古风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【像素复古风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张像素艺术风格的图片作为封面。整体呈现出 8-bit/16-bit 复古游戏美学——方块感十足、色彩鲜明、怀旧感强，充满复古游戏的趣味与活力。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象事物，以像素风格重新演绎。例如：若文章关于编程，可以是一台像素化的复古电脑、一个像素机器人；若关于游戏，可以是一个像素英雄、一把像素宝剑；若关于生活，可以是一杯像素咖啡、一个像素盆栽。必须是能被像素化表现的实体，绝对不要生成复杂的真实照片感）。\n" +
            "【像素细节】：使用清晰的像素块构成——每个色块边缘锐利方正，可见明显的像素网格感。色板限制在复古游戏机色系内（如 FC 红、Game Boy 绿、NES 灰褐等），颜色数量有限但对比鲜明。具有 dithering（抖动）纹理表现渐变与阴影。整体像素密度适中（约 32x32 到 64x64 风格放大）。\n" +
            "【构图与场景】：采用复古游戏画面构图——主体居中或站在像素地面上，背景用简单的像素天空/山脉/云朵构成。可加入像素化的 UI 元素（心形生命、金币、分数数字）增强游戏感。画面层次分明，有 8-bit 平台跳跃游戏的经典视觉感受。\n" +
            "【氛围与背景】：背景是纯色渐变或简单像素天空，搭配像素云朵/星星/月亮。整体氛围怀旧又充满乐趣，仿佛一张经典复古游戏的封面或标题画面，让人想起童年玩游戏机的美好时光。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 3D渲染卡通风 - LLM 系统提示词：按 3D 卡通渲染美学公式生成完整生图 prompt */
    public static final String RENDER3D_REFINE_PROMPT =
            "你是一个专注于3D渲染卡通风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【3D渲染卡通风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张 3D 卡通渲染风格的图片作为封面。整体呈现出圆润可爱的 3D 黏土/塑胶质感——色彩明快、造型立体、光影柔和，充满现代产品级 3D 插画的精致感。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取1-2个与文章核心概念相关的具象事物，以 3D 卡通风格重新设计。例如：若文章关于科技，可以是一个悬浮的 3D 图标、一个卡通机器人；若关于学习，可以是一摞 3D 书本、一个卡通灯泡；若关于健康，可以是一个 3D 苹果、一个卡通跑步小人。必须是立体造型的实体，绝对不要生成扁平插画）。\n" +
            "【材质与质感】：使用柔和的 3D 材质——主体表面光滑圆润，有轻微的磨砂或黏土质感，高光柔和不刺眼。色彩饱和度适中偏高，颜色干净明快。有 subsurface scattering（次表面散射）般的通透感，仿佛软糖或树脂材质。边缘圆润饱满，没有尖锐棱角。\n" +
            "【布光与构图】：采用柔和的三点布光——主光暖色调柔和从上照射，辅光冷色补暗部，背景光勾勒轮廓。阴影柔和模糊（soft shadow），呈现漫反射质感。构图主体居中略偏上，周围有漂浮的小元素环绕，画面有深度和层次感。\n" +
            "【背景与氛围】：背景为渐变色或纯色，搭配柔和的光斑与漂浮粒子。整体氛围现代、活泼、精致，有 Dribbble 风格 3D 插画的高级感，适合科技产品与创意内容的视觉呈现。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 极简几何风 - LLM 系统提示词：按极简几何抽象美学公式生成完整生图 prompt */
    public static final String MINIMAL_REFINE_PROMPT =
            "你是一个专注于极简几何风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【极简几何风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张极简几何风格的图片作为封面。整体呈现出极简主义设计美学——简洁克制、几何纯粹、色彩高级，充满现代设计的秩序感与视觉张力。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须将文章核心概念抽象为1-3个几何形态来表达。例如：若文章关于增长，可以是一个向上的箭头或阶梯状几何；若关于平衡，可以是一组对称的圆形或方块；若关于创新，可以是一个不规则的多边形或渐变圆形。必须用纯几何形态抽象表达，绝对不要生成具象物品或人物）。\n" +
            "【几何与色彩】：使用基础几何形状——圆形、方形、三角形、线条、渐变圆环等。色彩为高级感配色（莫兰迪色、大地色、黑白灰加一抹亮色、或同色系渐变），色彩数量少但搭配精致。有微妙的渐变、透明度变化、噪点纹理或颗粒质感增加细节层次。\n" +
            "【构图与空间】：采用极简构图——大量留白，几何元素精准放置在黄金分割或对称位置。画面空间感强，元素之间有呼吸感，不拥挤不繁复。可运用正负形、重叠、渐变等手法增加层次。整体排版克制而有设计感。\n" +
            "【氛围与背景】：背景为纯色或微妙的渐变/纹理，干净不抢戏。整体氛围理性、高级、宁静，仿佛一张现代设计海报，具有强烈的视觉识别度与品牌感。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    /** 复古拼贴风 - LLM 系统提示词：按复古杂志拼贴美学公式生成完整生图 prompt */
    public static final String COLLAGE_REFINE_PROMPT =
            "你是一个专注于复古拼贴风封面的AI提示词工程师。请根据提供的文章内容，严格按照以下【复古拼贴风·封面提示词公式】提炼并生成最终的英文生图提示词：\n\n" +
            "【基础设定】：生成一张复古杂志拼贴风格的图片作为封面。整体呈现出手工拼贴美学——元素混搭、质感丰富、怀旧文艺，充满创意灵感与复古杂志的视觉魅力。\n" +
            "【核心主体】：画面的核心视觉焦点是（必须提取2-3个与文章核心概念相关的意象元素，以拼贴方式组合。例如：若文章关于旅行，可以是一张老照片+一张邮票+一张机票；若关于阅读，可以是一本旧书+一副眼镜+一片压花；若关于梦想，可以是一个热气球+一朵云+一把钥匙。元素之间可以大小比例失调、可以超出边界，拼贴感就是要打破常规）。\n" +
            "【材质与质感】：使用复古拼贴的材质感——元素边缘有手撕纸的毛边或剪刀裁切的痕迹，可见纸张纹理、印刷网点、旧照片的褪色感。有胶带、订书钉、回形针、蜡封等拼贴道具元素增加真实感。整体色调偏暖、略微褪色，有年代感的黄调或棕调。\n" +
            "【构图与层次】：采用多层拼贴构图——元素错落叠放，有前后层次关系，部分元素可以超出画面边界。可加入几何色块、手写字体、线条涂鸦等装饰元素。构图看似随意但整体平衡，有手工创作的即兴感与创意张力。\n" +
            "【氛围与背景】：背景为旧纸张、牛皮纸、或做旧的墙面纹理。整体氛围怀旧文艺、充满创意灵感，仿佛一本复古杂志的内页或手账本的一页，既有年代感又不失现代审美。\n\n" +
            "输出要求：\n" +
            "1. 请将以上要素融合成一段连贯的英文描述，约50至80词，信息密度高，避免冗余修饰。\n" +
            "2. 直接输出最终的生图提示词！绝对不要输出“【基础设定】”、“【核心主体】”等前缀标签，也不要有任何解释说明！";

    // ==================== 宽高比描述 ====================

    private static String aspectRatioDesc(String size) {
        if (size == null || size.isBlank()) return "16:9 landscape aspect ratio";
        return switch (size.toLowerCase()) {
            case "1:1" -> "1:1 square aspect ratio";
            case "16:9" -> "16:9 landscape aspect ratio";
            case "9:16" -> "9:16 portrait aspect ratio";
            case "4:3" -> "4:3 landscape aspect ratio";
            case "3:4" -> "3:4 portrait aspect ratio";
            default -> size + " aspect ratio";
        };
    }

    // ==================== 获取模板专用 refine_prompt ====================

    /**
     * 根据模板类型返回对应的 LLM 系统提示词。
     * <ul>
     *   <li>object:    物品类真实感公式</li>
     *   <li>portrait:  人物类真实感公式</li>
     *   <li>felt:      毛毡Q版可爱风公式</li>
     *   <li>cyberpunk: 赛博朋克霓虹风公式</li>
     *   <li>watercolor:水彩手绘风公式</li>
     *   <li>ink:       国风水墨画公式</li>
     *   <li>pixel:     像素复古风公式</li>
     *   <li>3d:        3D渲染卡通风公式</li>
     *   <li>minimal:   极简几何风公式</li>
     *   <li>collage:   复古拼贴风公式</li>
     *   <li>custom:    用户在后台填写的自定义 refine_prompt（为空时降级为 object）</li>
     * </ul>
     */
    public static String getRefinePrompt(ImageConfigDto config) {
        if (config.useFeltTemplate()) {
            return FELT_REFINE_PROMPT;
        }
        if (config.useCyberpunkTemplate()) {
            return CYBERPUNK_REFINE_PROMPT;
        }
        if (config.useWatercolorTemplate()) {
            return WATERCOLOR_REFINE_PROMPT;
        }
        if (config.useInkTemplate()) {
            return INK_REFINE_PROMPT;
        }
        if (config.usePixelTemplate()) {
            return PIXEL_REFINE_PROMPT;
        }
        if (config.use3dTemplate()) {
            return RENDER3D_REFINE_PROMPT;
        }
        if (config.useMinimalTemplate()) {
            return MINIMAL_REFINE_PROMPT;
        }
        if (config.useCollageTemplate()) {
            return COLLAGE_REFINE_PROMPT;
        }
        if (config.usePortraitTemplate()) {
            return PORTRAIT_REFINE_PROMPT;
        }
        if (config.useCustomTemplate()) {
            String custom = config.getCustomRefinePrompt();
            return (custom != null && !custom.isBlank()) ? custom : OBJECT_REFINE_PROMPT;
        }
        return OBJECT_REFINE_PROMPT;
    }

    // ==================== 最终 prompt 拼接 ====================

    /**
     * 物品类模板拼接最终英文生图 prompt。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a monitor and a mechanical keyboard"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildObjectPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a laptop and a cup of coffee";

        return "High-resolution photorealistic image as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Rich physical textures: " + OBJECT_MATERIAL + ". " +
                "Natural realistic colors, no AI neon glow or plastic feel. " +
                "Shot with " + OBJECT_LENS + ", natural perspective, strong depth of field. " +
                OBJECT_SCENE + ".";
    }

    /**
     * 人物类模板拼接最终英文生图 prompt。
     *
     * @param characterAction LLM 提炼的人物动作（plain 模式下可取文章标题/内容；如 "looking intently at a glowing screen"）
     * @param config          生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildPortraitPrompt(String characterAction, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String action = (characterAction != null && !characterAction.isBlank())
                ? characterAction.trim() : "looking at a glowing screen with concentration";

        return "Ultra-high-quality photorealistic portrait as article cover, " + aspect + ". " +
                "The subject is " + PORTRAIT_CHARACTER + ", " + action + ". " +
                PORTRAIT_EMOTION + ", preserving real skin texture, pores and minor blemishes. " +
                "Wearing " + PORTRAIT_OUTFIT + " with realistic fabric texture. " +
                "Shot with " + PORTRAIT_CAMERA + ", dynamic composition. " +
                PORTRAIT_SCENE_LIGHT + ", natural light transitions.";
    }

    /**
     * 毛毡Q版可爱风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a cute felt bear holding a laptop"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildFeltPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a cute needle-felted little bear holding a tiny laptop";

        return "Wool felt craft style chibi cute illustration as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Realistic needle-felting texture: fluffy wool fibers, visible needling marks, slightly fuzzy surface. " +
                "Pastel macaron color palette (cream white, mint green, sakura pink, dusty blue). " +
                "Chibi proportions (large head, 2-3 head ratio), rounded silhouette, exaggerated adorable expression. " +
                "Centered composition with generous negative space, small felt decorations like flowers or stars. " +
                "Soft even lighting, no harsh shadows, warm healing atmosphere. " +
                "Solid or gentle gradient pastel background.";
    }

    /**
     * 赛博朋克霓虹风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a floating holographic monitor"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildCyberpunkPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a floating holographic monitor with neural interface cables";

        return "Cyberpunk neon future style image as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Cyber-industrial textures: brushed metal, carbon fiber, acrylic shell, exposed LED strips and neon tubes, circuit board patterns. " +
                "Surface with subtle scratches, oil stains and water marks for realism. " +
                "Color palette: deep blue-purple base with magenta, cyan and electric green neon highlights. " +
                "Low-angle shot with dutch tilt, wide-angle lens tension, strong depth of field with bokeh light streaks. " +
                "Rainy cyberpunk city night background: wet streets reflecting neon signs, distant holographic billboards, drifting steam and rain. " +
                "Sharp high-contrast neon lighting, dramatic cyberpunk night atmosphere.";
    }

    /**
     * 水彩手绘风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a cup of coffee and an open book"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildWatercolorPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a cup of coffee and an open book on a wooden table";

        return "Watercolor hand-painted illustration style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Transparent watercolor texture: visible water stains and bleeding at edges, soft fuzzy borders, " +
                "paint graininess, paper texture, natural brush stroke traces. " +
                "Fresh soft color palette, warm tones or Morandi colors, overall harmonious and unified tone. " +
                "Clean fresh composition, breathing room, decorative illustration feel with small accent elements. " +
                "Background with large white space or light color wash, scattered watercolor splatters and textures. " +
                "Warm healing, literary and fresh atmosphere, like a page from a hand-drawn sketchbook.";
    }

    /**
     * 国风水墨画模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a lone pine tree on misty mountain"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildInkPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a lone ancient pine tree on a misty mountain peak";

        return "Traditional Chinese ink wash painting style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Authentic brush and ink techniques: varying ink tones from dark black to pale grey, " +
                "visible brush strokes with dry brush texture, ink wash bleeding and diffusion effects. " +
                "Subtle mineral color accents of ochre, indigo and gamboge, overall warm ancient tone. " +
                "Traditional Chinese composition: generous negative space, asymmetrical layout, " +
                "vacant-actual interplay, sparse and elegant arrangement, flowing qi energy. " +
                "Background with faint ink-washed distant mountains, drifting clouds or misty river, " +
                "creating ethereal and profound artistic conception. " +
                "Zen-like tranquility, literati painting aesthetic, poetic and meditative atmosphere.";
    }

    /**
     * 像素复古风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a pixel robot with a retro computer"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildPixelPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a cute pixel robot next to a retro computer";

        return "Pixel art retro game style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Sharp square pixel blocks with visible pixel grid, 8-bit / 16-bit aesthetic. " +
                "Limited retro game color palette (FC red, Game Boy green, NES grays), high contrast. " +
                "Dithering texture for gradients and shadows, pixel density like 32x32 to 64x64 style upscaled. " +
                "Classic platformer game composition, pixel sky with pixel clouds/mountains background. " +
                "Optional pixel UI elements: heart icons, coins, score digits. " +
                "Nostalgic fun atmosphere, like a classic retro game cover or title screen.";
    }

    /**
     * 3D渲染卡通风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a floating 3D light bulb icon"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String build3dPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a floating 3D glowing light bulb icon";

        return "3D cartoon render style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Smooth rounded 3D shapes with soft matte or clay texture, gentle highlights, no sharp edges. " +
                "Bright clean saturated colors, subsurface scattering-like translucency, gummy or resin material feel. " +
                "Soft three-point lighting: warm key light from above, cool fill light, rim light for silhouette. " +
                "Soft blurred shadows, diffuse rendering quality. " +
                "Subject centered slightly above, surrounded by small floating decorative elements. " +
                "Gradient or solid color background with soft light spots and floating particles. " +
                "Modern, playful, polished Dribbble-style 3D illustration aesthetic.";
    }

    /**
     * 极简几何风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "an abstract geometric composition"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildMinimalPrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "an abstract geometric composition with circles and lines";

        return "Minimalist geometric abstract style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Basic geometric shapes: circles, squares, triangles, lines, gradient rings. " +
                "Sophisticated color palette: Morandi tones, earth tones, black-white-grey with an accent, or monochromatic gradient. " +
                "Subtle gradients, transparency variations, noise texture or grain for detail. " +
                "Minimalist composition: generous negative space, elements placed at golden ratio or symmetrical positions. " +
                "Strong breathing room, positive-negative shape play, overlapping layers. " +
                "Clean solid color or subtle gradient/noise background. " +
                "Rational, premium, serene atmosphere, modern design poster aesthetic.";
    }

    /**
     * 复古拼贴风模板拼接最终英文生图 prompt（plain 模式默认拼接）。
     *
     * @param coreSubject LLM 提炼的核心主体（plain 模式下可取文章标题/内容；如 "a vintage collage with old photos and stamps"）
     * @param config      生图配置（取宽高比）
     * @return 拼接好的完整 prompt
     */
    public static String buildCollagePrompt(String coreSubject, ImageConfigDto config) {
        String aspect = aspectRatioDesc(config.getSize());
        String subject = (coreSubject != null && !coreSubject.isBlank())
                ? coreSubject.trim() : "a vintage collage with old photos, postage stamps and a handwritten letter";

        return "Vintage magazine collage style as article cover, " + aspect + ". " +
                "The core visual focus is " + subject + ". " +
                "Handmade collage texture: torn paper edges, scissor cut borders, paper grain, halftone print dots, faded photo look. " +
                "Collage props: washi tape, staples, paper clips, wax seals for authenticity. " +
                "Warm slightly faded color tone, aged yellow or sepia tint. " +
                "Multi-layered composition: overlapping elements at different scales, some extending beyond frame edges. " +
                "Decorative elements: geometric color blocks, handwritten text, doodle lines. " +
                "Old paper, kraft paper or textured wall background. " +
                "Nostalgic, artistic, creative atmosphere, like a vintage magazine page or scrapbook spread.";
    }
}
