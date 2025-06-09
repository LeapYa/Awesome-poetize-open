<template>
  <div>
    <div v-if="!$common.isEmpty(article)">
      <!-- 封面 -->
      <div class="article-head my-animation-slide-top">
        <!-- 背景图片 -->
        <el-image class="article-image my-el-image"
                  v-once
                  lazy
                  :src="article.articleCover"
                  fit="cover">
          <div slot="error" class="image-slot">
            <div class="article-image"></div>
          </div>
        </el-image>
        
          <!-- 添加语言切换按钮 -->
          <div class="article-language-switch">
            <el-button-group>
              <el-button size="mini" :type="currentLang === 'zh' ? 'primary' : 'default'" @click="switchLanguage('zh')">中文</el-button>
              <el-button size="mini" :type="currentLang === 'en' ? 'primary' : 'default'" @click="switchLanguage('en')">English</el-button>
            </el-button-group>
          </div>
        <!-- 文章信息 -->
        <div class="article-info-container">
          <div class="article-title">{{ articleTitle }}</div>
          <div class="article-info">
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path
                d="M510.4 65.5l259.69999999 0 1e-8 266.89999999c0 147.50000001-116.2 266.89999999-259.7 266.90000001-143.4 0-259.7-119.5-259.7-266.90000001 0.1-147.5 116.3-266.9 259.7-266.89999999z"
                fill="#FF9FCF"></path>
              <path
                d="M698.4 525.2l-13 0c53-48.4 86.5-117.8 86.5-195.20000001 0-10.2-0.7-20.3-1.8-30.19999999C613.8 377.50000001 438.6 444.9 266 437.7c15 33.4 36.7 63.1 63.5 87.5l-5.3 0c-122.6 0-225.5 88.1-248.8 204.1C340 677.2 597.7 609.2 862.2 585.7c-44.3-37.6-101.5-60.5-163.8-60.5z"
                fill="#FF83BB"></path>
              <path
                d="M862.2 585.7C597.7 609.2 340 677.2 75.4 729.3c-3.2 16.1-5 32.6-5 49.6 0 99.8 81.7 181.5 181.5 181.5l518.6 0c99.8 0 181.5-81.7 181.5-181.5 0.1-77.2-35-146.5-89.8-193.2z"
                fill="#FF5390"></path>
              <path
                d="M770.1 299.8C755.1 168 643.3 65.5 507.4 65.5c-146.1 0-264.5 118.4-264.5 264.5 0 38.4 8.3 74.8 23.1 107.7 172.6 7.2 347.8-60.2 504.1-137.9z"
                fill="#FF9FCF"></path>
              <path
                d="M436.4 282.1c0 24.1-19.6 43.7-43.7 43.7S349 306.2 349 282.1s19.6-43.7 43.7-43.7c24.19999999 0 43.7 19.6 43.7 43.7z"
                fill="#FFFFFF"></path>
              <path d="M625 282.1m-43.7 0a43.7 43.7 0 1 0 87.4 0 43.7 43.7 0 1 0-87.4 0Z" fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.username }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path d="M512 512m-512 0a512 512 0 1 0 1024 0 512 512 0 1 0-1024 0Z" fill="#409EFF"></path>
              <path
                d="M654.222222 256c-17.066667 0-28.444444 11.377778-28.444444 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444444 28.444445s28.444444-11.377778 28.444445-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444445-28.444444zM369.777778 256c-17.066667 0-28.444444 11.377778-28.444445 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444445 28.444445s28.444444-11.377778 28.444444-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444444-28.444444z"
                fill="#FFFFFF"></path>
              <path
                d="M725.333333 312.888889H711.111111v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-170.666666v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-14.222222c-22.755556 0-42.666667 19.911111-42.666667 42.666667v341.333333c0 22.755556 19.911111 42.666667 42.666667 42.666667h426.666666c22.755556 0 42.666667-19.911111 42.666667-42.666667v-341.333333c0-22.755556-19.911111-42.666667-42.666667-42.666667zM426.666667 654.222222h-56.888889c-17.066667 0-28.444444-11.377778-28.444445-28.444444s11.377778-28.444444 28.444445-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444444 28.444445s-11.377778 28.444444-28.444444 28.444444z m227.555555 0h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444445s-11.377778 28.444444-28.444445 28.444444z m0-113.777778h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444444h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444444s-11.377778 28.444444-28.444445 28.444444z"
                fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.createTime }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path d="M14.656 512a497.344 497.344 0 1 0 994.688 0 497.344 497.344 0 1 0-994.688 0z"
                    fill="#FF0000"></path>
              <path
                d="M374.976 872.64c-48.299-100.032-22.592-157.44 14.421-211.37 40.448-58.966 51.115-117.611 51.115-117.611s31.659 41.386 19.115 106.005c56.149-62.72 66.816-162.133 58.325-200.405 127.317 88.746 181.59 281.002 108.181 423.381C1016 652.501 723.093 323.2 672.277 285.867c16.939 37.333 20.054 100.032-14.101 130.474-58.027-219.84-201.664-265.002-201.664-265.002 16.96 113.536-61.781 237.397-137.344 330.24-2.816-45.163-5.632-76.544-29.483-119.808-5.333 82.176-68.373 149.269-85.29 231.445-22.912 111.637 17.237 193.173 170.581 279.424z"
                fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.viewCount }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path
                d="M113.834667 291.84v449.194667a29.013333 29.013333 0 0 0 28.842666 29.013333h252.928v90.453333l160.597334-90.453333h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333h-665.6a29.013333 29.013333 0 0 0-29.696 29.013333z"
                fill="#FFDEAD"></path>
              <path
                d="M809.130667 262.826667h-665.6a29.013333 29.013333 0 0 0-28.842667 29.013333v40.106667a29.013333 29.013333 0 0 1 28.842667-29.013334h665.6a29.013333 29.013333 0 0 1 29.013333 29.013334V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333z"
                fill="#FFF3DB"></path>
              <path
                d="M556.202667 770.048h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V362.837333s-59.733333 392.533333-724.309333 314.709334v63.488a29.013333 29.013333 0 0 0 28.842666 29.013333h253.098667v90.453333z"
                fill="#F2C182"></path>
              <path
                d="M619.008 632.32l101.888-35.157333-131.754667-76.117334 29.866667 111.274667zM891.904 148.992a61.44 61.44 0 0 0-84.138667 22.528l-19.968 34.133333 106.666667 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-22.528-84.138667z"
                fill="#69BAF9"></path>
              <path d="M775.338667 198.775467l131.669333 76.032-186.026667 322.218666-131.6864-76.032z"
                    fill="#F7FBFF"></path>
              <path
                d="M775.168 198.826667l-5.290667 9.216 59.221334 34.133333a34.133333 34.133333 0 0 1 12.458666 46.592l-139.946666 242.346667a34.133333 34.133333 0 0 1-46.762667 12.629333l-59.050667-34.133333-6.656 11.434666 88.746667 51.2L720.896 597.333333l186.026667-322.56z"
                fill="#D8E3F0"></path>
              <path
                d="M616.448 622.592l2.56 9.728 101.888-35.157333-44.885333-25.941334-59.562667 51.370667zM891.904 148.992c-1.024 0-2.218667-0.853333-3.242667-1.536A61.610667 61.610667 0 0 1 887.466667 204.8l-19.968 34.133333-73.728-42.496-5.12 8.704 106.666666 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-23.381333-83.626667z"
                fill="#599ED4"></path>
              <path
                d="M265.898667 417.621333H494.933333a17.066667 17.066667 0 1 0 0-34.133333H265.898667a17.066667 17.066667 0 1 0 0 34.133333zM265.898667 533.504H494.933333a17.066667 17.066667 0 0 0 0-34.133333H265.898667a17.066667 17.066667 0 0 0 0 34.133333z"
                fill="#3D3D63"></path>
              <path
                d="M959.488 354.645333a99.84 99.84 0 0 0-23.722667-127.488 78.677333 78.677333 0 0 0-142.848-64.170666l-11.605333 20.138666a17.066667 17.066667 0 0 0-20.821333 7.168l-32.085334 55.466667H142.677333a46.250667 46.250667 0 0 0-45.909333 46.08v449.194667a46.08 46.08 0 0 0 45.909333 46.08h236.032v73.386666a17.066667 17.066667 0 0 0 8.362667 14.848 17.066667 17.066667 0 0 0 8.704 2.218667 17.066667 17.066667 0 0 0 8.362667-2.218667l156.672-88.234666h248.32a46.08 46.08 0 0 0 46.08-46.08V398.677333L921.6 283.306667a17.066667 17.066667 0 0 0-4.266667-21.504l1.877334-3.413334a65.365333 65.365333 0 0 1 10.410666 79.189334l-53.077333 91.989333a56.832 56.832 0 0 0 20.821333 77.653333 17.066667 17.066667 0 0 0 24.234667-6.314666 17.066667 17.066667 0 0 0-6.997333-23.04 23.04 23.04 0 0 1-8.362667-31.061334z m-138.410667 386.389334a11.946667 11.946667 0 0 1-11.946666 11.946666H556.202667a17.066667 17.066667 0 0 0-8.362667 2.218667l-134.997333 76.117333v-61.269333a17.066667 17.066667 0 0 0-17.066667-17.066667H142.677333a11.946667 11.946667 0 0 1-11.776-11.946666V291.84a11.946667 11.946667 0 0 1 11.776-11.946667h565.930667L574.464 512a17.066667 17.066667 0 0 0-1.706667 12.970667L597.333333 615.253333H265.898667a17.066667 17.066667 0 1 0 0 34.133334h352.938666a17.066667 17.066667 0 0 0 5.802667 0l102.4-35.328a17.066667 17.066667 0 0 0 9.216-7.509334l85.333333-147.968z m-204.8-184.661334l63.829334 36.864-49.322667 17.066667z m206.848-170.666666v1.365333l-108.373333 186.709333-102.4-59.050666L781.482667 221.866667l102.4 59.050666z m76.458667-161.28L887.466667 244.224l-76.970667-44.373333 11.264-19.797334a44.544 44.544 0 1 1 77.141333 44.544z"
                fill="#3D3D63"></path>
            </svg>
            <span>&nbsp;{{ article.commentCount }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path
                d="M510.671749 348.792894S340.102978 48.827055 134.243447 254.685563C-97.636714 486.565724 510.671749 913.435858 510.671749 913.435858s616.107079-419.070494 376.428301-658.749272c-194.095603-194.096626-376.428302 94.106308-376.428301 94.106308z"
                fill="#FF713C"></path>
              <path
                d="M510.666632 929.674705c-3.267417 0-6.534833-0.983397-9.326413-2.950192-16.924461-11.872399-414.71121-293.557896-435.220312-529.448394-5.170766-59.482743 13.879102-111.319341 56.643068-154.075121 51.043536-51.043536 104.911398-76.930113 160.095231-76.930114 112.524796 0 196.878996 106.48115 228.475622 153.195078 33.611515-45.214784 122.406864-148.20646 234.04343-148.20646 53.930283 0 105.46603 24.205285 153.210428 71.941496 45.063335 45.063335 64.954361 99.200326 59.133795 160.920016C935.306982 641.685641 536.758893 915.327952 519.80271 926.859589a16.205077 16.205077 0 0 1-9.136078 2.815116zM282.857183 198.75574c-46.25344 0-92.396363 22.682605-137.127124 67.413365-36.149315 36.157501-51.614541 78.120218-47.25321 128.291898 17.575284 202.089671 352.199481 455.119525 412.332023 499.049037 60.434417-42.86732 395.406538-289.147446 414.567947-492.458945 4.933359-52.344159-11.341303-96.465029-49.759288-134.88199-41.431621-41.423435-85.24243-62.424748-130.242319-62.424748-122.041544 0-220.005716 152.203494-220.989114 153.742547-3.045359 4.806469-8.53335 7.883551-14.101159 7.534603a16.257266 16.257266 0 0 1-13.736863-8.184403c-0.902556-1.587148-91.569532-158.081365-213.690893-158.081364z"
                fill="#885F44"></path>
            </svg>
            <span>&nbsp;{{ article.likeCount }}</span>
          </div>
        </div>

        <div class="article-info-news"
             @click="weiYanDialogVisible = true"
             v-if="!$common.isEmpty($store.state.currentUser) && $store.state.currentUser.id === article.userId">
          <svg width="30" height="30" viewBox="0 0 1024 1024">
            <path d="M0 0h1024v1024H0V0z" fill="#202425" opacity=".01"></path>
            <path
              d="M989.866667 512c0 263.918933-213.947733 477.866667-477.866667 477.866667S34.133333 775.918933 34.133333 512 248.081067 34.133333 512 34.133333s477.866667 213.947733 477.866667 477.866667z"
              fill="#FF7744"></path>
            <path
              d="M512 221.866667A51.2 51.2 0 0 1 563.2 273.066667v187.733333H750.933333a51.2 51.2 0 0 1 0 102.4h-187.733333V750.933333a51.2 51.2 0 0 1-102.4 0v-187.733333H273.066667a51.2 51.2 0 0 1 0-102.4h187.733333V273.066667A51.2 51.2 0 0 1 512 221.866667z"
              fill="#FFFFFF"></path>
          </svg>
        </div>
      </div>
      <!-- 文章 -->
      <div style="background: var(--background);">
        <div class="article-container my-animation-slide-bottom">
          <div v-if="!$common.isEmpty(article.videoUrl)" style="margin-bottom: 20px">
            <videoPlayer :url="{src: $common.decrypt(article.videoUrl)}"
                         :cover="article.articleCover">
            </videoPlayer>
          </div>

          <!-- 最新进展 -->
          <div v-if="!$common.isEmpty(treeHoleList)" class="process-wrap">
            <el-collapse accordion value="1">
              <el-collapse-item title="最新进展" name="1">
                <process :treeHoleList="treeHoleList" @deleteTreeHole="deleteTreeHole"></process>
              </el-collapse-item>
            </el-collapse>

            <hr>
          </div>

          <!-- 文章内容 -->
          <div v-if="isLoading" class="entry-content">
            <el-skeleton :rows="10" animated />
          </div>
          <div v-else v-html="articleContentHtml" class="entry-content" :lang="currentLang"></div>
          <!-- 最后更新时间 -->
          <div class="article-update-time">
            <span>文章最后更新于 {{ article.updateTime }}</span>
          </div>
          <!-- 分类 -->
          <div class="article-sort">
            <span @click="$router.push({path: '/sort', query: {sortId: article.sortId, labelId: article.labelId}})">{{ article.sort.sortName +" ▶ "+ article.label.labelName}}</span>
          </div>
          <!-- 作者信息 -->
          <blockquote>
            <div>
              作者：{{article.username}}
            </div>
            <div>
              <span>版权&许可请详阅</span>
              <span style="color: #38f;cursor: pointer"
                    @click="copyrightDialogVisible = true">
                版权声明
              </span>
            </div>
          </blockquote>
          <!-- 订阅 -->
          <div class="myCenter" id="article-like" @click="subscribeLabel()">
            <i class="el-icon-thumb article-like-icon" :class="{'article-like': subscribe}"></i>
          </div>

          <!-- 评论 -->
          <div v-if="article.commentStatus === true">
            <comment :type="'article'" :source="article.id" :userId="article.userId"></comment>
          </div>
        </div>

        <div id="toc" class="toc"></div>
      </div>

      <div style="background: var(--background)">
        <myFooter></myFooter>
      </div>
    </div>

    <div id="toc-button" @click="clickTocButton()">
      <i class="fa fa-align-justify" aria-hidden="true"></i>
    </div>

    <el-dialog title="版权声明"
               :visible.sync="copyrightDialogVisible"
               width="80%"
               :append-to-body="true"
               class="article-copy"
               center>
      <div style="display: flex;align-items: center;flex-direction: column">
        <el-avatar shape="square" :size="35" :src="$store.state.webInfo.avatar"></el-avatar>
        <div class="copyright-container">
          <p>
            {{ $store.state.webInfo.webName }}是指运行在{{ $constant.host }}域名及相关子域名上的网站，本条款描述了{{ $store.state.webInfo.webName }}的网站版权声明：
          </p>
          <ul>
            <li>
              {{ $store.state.webInfo.webName }}提供的所有文章、展示的图片素材等内容部分来源于互联网平台，仅供学习参考。如有侵犯您的版权，请联系{{ $store.state.webInfo.webName }}负责人，{{ $store.state.webInfo.webName }}承诺将在一个工作日内改正。
            </li>
            <li>
              {{ $store.state.webInfo.webName }}不保证网站内容的全部准确性、安全性和完整性，请您在阅读、下载及使用过程中自行确认，{{ $store.state.webInfo.webName }}亦不承担上述资源对您造成的任何形式的损失或伤害。
            </li>
            <li>未经{{ $store.state.webInfo.webName }}允许，不得盗链、盗用本站内容和资源。</li>
            <li>
              {{ $store.state.webInfo.webName }}旨在为广大用户提供更多的信息；{{ $store.state.webInfo.webName }}不保证向用户提供的外部链接的准确性和完整性，该外部链接指向的不由本站实际控制的任何网页上的内容，{{ $store.state.webInfo.webName }}对其合法性亦概不负责，亦不承担任何法律责任。
            </li>
            <li>
              {{ $store.state.webInfo.webName }}中的文章/视频（包括转载文章/视频）的版权仅归原作者所有，若作者有版权声明或文章从其它网站转载而附带有原所有站的版权声明者，其版权归属以附带声明为准；文章仅代表作者本人的观点，与{{ $store.state.webInfo.webName }}立场无关。
            </li>
            <li>
              {{ $store.state.webInfo.webName }}自行编写排版的文章均采用
              <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" style="color: #38f;text-decoration: none;">
                知识共享署名-非商业性使用-相同方式共享 4.0 国际许可协议
              </a>
            </li>
            <li>
              许可协议标识：
              <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/">
                <img alt="知识共享许可协议"
                     src="https://i.creativecommons.org/l/by-nc-sa/4.0/88x31.png"
                     style="margin-top: 5px">
              </a>
            </li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <el-dialog title="最新进展"
               :visible.sync="weiYanDialogVisible"
               width="40%"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <div class="myCenter" style="margin-bottom: 20px">
          <el-date-picker
            v-model="newsTime"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
            align="center"
            placeholder="选择日期时间">
          </el-date-picker>
        </div>
        <commentBox :disableGraffiti="true"
                    @submitComment="submitWeiYan">
        </commentBox>
      </div>
    </el-dialog>

    <!-- 微信 -->
    <el-dialog title="密码"
               :modal="false"
               :visible.sync="showPasswordDialog"
               width="25%"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <div>
          <div class="password-content">{{tips}}</div>
        </div>
        <div style="margin: 20px auto">
          <el-input maxlength="30" v-model="password"></el-input>
        </div>
        <div style="display: flex;justify-content: center">
          <proButton :info="'提交'"
                     @click.native="submitPassword()"
                     :before="$constant.before_color_2"
                     :after="$constant.after_color_2">
          </proButton>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
  const myFooter = () => import( "./common/myFooter");
  const comment = () => import( "./comment/comment");
  const process = () => import( "./common/process");
  const commentBox = () => import( "./comment/commentBox");
  const proButton = () => import( "./common/proButton");
  const videoPlayer = () => import( "./common/videoPlayer");
  import MarkdownIt from 'markdown-it';
  import $ from 'jquery';
  import axios from 'axios';

  export default {
    components: {
      myFooter,
      comment,
      commentBox,
      proButton,
      process,
      videoPlayer
    },

    data() {
      return {
        id: this.$route.params.id,
        subscribe: false,
        article: {},
        articleContentHtml: "",
        treeHoleList: [],
        weiYanDialogVisible: false,
        copyrightDialogVisible: false,
        newsTime: "",
        showPasswordDialog: false,
        password: "",
        tips: "",
        scrollTop: 0,
        hasInitTocbot: false,
        metaTags: null,
        metaTagRetryCount: 0,
        isLoadingMeta: false,
        currentLang: 'zh', // 默认中文
        isLoading: false,
        translatedTitle: '',
        translatedContent: '',
        tempComment: null // 存储临时评论内容
      };
    },

    head() {
      if (!this.metaTags) {
        return {
          title: 'Poetize博客',
          meta: []
        };
      }
      
      return {
        title: this.metaTags.title,
        meta: [
          { name: 'description', content: this.metaTags.description },
          { name: 'keywords', content: this.metaTags.keywords },
          { name: 'author', content: this.metaTags.author },
          { property: 'og:title', content: this.metaTags.title },
          { property: 'og:description', content: this.metaTags.description },
          { property: 'og:type', content: 'article' },
          { property: 'og:url', content: this.metaTags['og:url'] },
          { property: 'og:image', content: this.metaTags['og:image'] },
          { name: 'twitter:card', content: this.metaTags['twitter:card'] },
          { name: 'twitter:title', content: this.metaTags.title },
          { name: 'twitter:description', content: this.metaTags.description },
          { name: 'twitter:image', content: this.metaTags['twitter:image'] },
          { property: 'article:published_time', content: this.metaTags['article:published_time'] },
          { property: 'article:modified_time', content: this.metaTags['article:modified_time'] }
        ]
      }
    },

    created() {
      if (!this.$common.isEmpty(this.id)) {
        this.getArticle(localStorage.getItem("article_password_" + this.id));

        if ("0" !== localStorage.getItem("showSubscribe")) {
          this.$notify({
            title: '文章订阅',
            type: 'success',
            message: '点击文章下方小手 - 订阅/取消订阅专栏（标签）',
            duration: 0,
            onClose: () => localStorage.setItem("showSubscribe", "0")
          });
        }
      }

      // 获取顺序：URL参数 > 用户保存的偏好 > 浏览器语言
      const urlParams = new URLSearchParams(window.location.search);
      const langParam = urlParams.get('lang');
      const savedLang = localStorage.getItem('preferredLanguage');
      
      if (langParam === 'en' || langParam === 'zh') {
        // URL参数优先
        this.currentLang = langParam;
      } else if (savedLang === 'en' || savedLang === 'zh') {
        // 其次是用户保存的偏好
        this.currentLang = savedLang;
        // 更新URL以反映语言选择
        this.updateUrlWithLanguage(savedLang);
      } else {
        // 最后是浏览器语言
        const browserLang = navigator.language || navigator.userLanguage;
        if (browserLang.toLowerCase().startsWith('en')) {
          this.currentLang = 'en';
          // 更新URL以反映语言选择
          this.updateUrlWithLanguage('en');
        } else {
          this.currentLang = 'zh'; // 默认中文
        }
      }
      
      // 设置HTML元素的lang属性
      document.documentElement.setAttribute('lang', this.currentLang);
      
      // 文章页面加载时触发看板娘检查
      this.$nextTick(() => {
        // 延迟触发事件，确保页面元素已加载
        setTimeout(() => {
          console.log('文章页面触发看板娘检查事件');
          if (document && document.dispatchEvent) {
            document.dispatchEvent(new Event('checkWaifu'));
          }
        }, 1000);
      });
    },

    mounted() {
      window.addEventListener("scroll", this.onScrollPage);
      this.getTocbot();
      
      // 添加看板娘初始化检查
      this.$nextTick(() => {
        // 检查当前配置是否启用看板娘
        const checkWaifuEnabled = () => {
          try {
            // 从本地存储获取配置
            const webInfoStr = localStorage.getItem('webInfo');
            if (webInfoStr) {
              const webInfoData = JSON.parse(webInfoStr);
              // 处理两种可能的数据格式
              if (webInfoData.data && webInfoData.data.enableWaifu !== undefined) {
                return webInfoData.data.enableWaifu === true;
              } else if (webInfoData.enableWaifu !== undefined) {
                return webInfoData.enableWaifu === true;
              }
            }
            return this.$store.state.webInfo.enableWaifu === true;
          } catch (e) {
            console.error('检查看板娘状态出错:', e);
            return false;
          }
        };
        
        // 检查是否已加载Live2D
        const checkLive2DLoaded = () => {
          return (
            typeof window.loadlive2d === 'function' && 
            document.getElementById('waifu') && 
            document.getElementById('live2d')
          );
        };
        
        // 检查看板娘是否显示
        const checkWaifuVisible = () => {
          const waifu = document.getElementById('waifu');
          return waifu && 
                waifu.style.display !== 'none' && 
                waifu.style.bottom !== '-500px';
        };
        
        // 检查并在需要时通过事件触发看板娘检查
        setTimeout(() => {
          if (checkWaifuEnabled()) {
            if (!checkLive2DLoaded() || !checkWaifuVisible()) {
              console.log('检测到看板娘加载异常，尝试重新初始化');
              
              // 使用事件驱动方式加载看板娘，避免直接操作DOM
              if (!localStorage.getItem("waifu-display")) {
                // 触发检查事件，让live2d.js完成初始化
                document.dispatchEvent(new Event('checkWaifu'));
              }
            }
          }
        }, 2000); // 延迟2秒检查，确保页面完全加载
      });
      
      // 检查是否有临时保存的评论
      this.checkTempComment();
      
      // 监听路由变化，检查是否从登录页面返回
      this.$watch(() => this.$route.query, (newQuery) => {
        if (newQuery.hasComment === 'true') {
          // 从登录页面返回且带有评论标记
          this.$nextTick(() => {
            this.checkTempComment();
          });
        }
      });
    },

    destroyed() {
      window.removeEventListener("scroll", this.onScrollPage);
    },

    watch: {
      scrollTop(scrollTop, oldScrollTop) {
        let isShow = scrollTop - window.innerHeight > 30;
        if (isShow) {
          $("#toc-button").css("bottom", "14.1vh");
        } else {
          $("#toc-button").css("bottom", "8vh");
        }
      },
      '$route.params.id': function() {
        // 重置翻译内容
        this.translatedTitle = '';
        this.translatedContent = '';
        
        // 获取文章
        this.getArticle();
      },
      '$route': function(newRoute, oldRoute) {
        // 检查变化是否仅仅是语言参数的变化
        // 如果仅是语言参数变化，不重新加载文章
        if (newRoute.params.id === oldRoute.params.id) {
          const newLang = newRoute.query.lang;
          const oldLang = oldRoute.query.lang;
          
          if (newLang !== oldLang) {
            // 仅语言参数变化，不重新获取文章，避免增加热度计数
            console.log('仅语言参数变化，不重新获取文章');
            return;
          }
        }
      }
    },

    computed: {
      articleTitle() {
        return this.currentLang === 'en' && this.translatedTitle ? this.translatedTitle : this.article.articleTitle;
      }
    },

    methods: {
      clickTocButton() {
        let display = $(".toc");
        if ("none" === display.css("display")) {
          display.css("display", "unset");
        } else {
          display.css("display", "none");
        }
      },
      subscribeLabel() {
        if (this.$common.isEmpty(this.$store.state.currentUser)) {
          this.$message({
            message: "请先登录！",
            type: "error"
          });
          return;
        }

        this.$confirm('确认' + (this.subscribe ? '取消订阅' : '订阅') + '专栏【' + this.article.label.labelName + '】？' + (this.subscribe ? "" : "订阅专栏后，该专栏发布新文章将通过邮件通知订阅用户。"), this.subscribe ? "取消订阅" : "文章订阅", {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/user/subscribe", {
            labelId: this.article.labelId,
            flag: !this.subscribe
          })
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.$store.commit("loadCurrentUser", res.data);
              }
              this.subscribe = !this.subscribe;
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消!'
          });
        });
      },
      submitPassword() {
        if (this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请先输入密码！",
            type: "error"
          });
          return;
        }

        this.getArticle(this.password);
      },
      deleteTreeHole(id) {
        if (this.$common.isEmpty(this.$store.state.currentUser)) {
          this.$message({
            message: "请先登录！",
            type: "error"
          });
          return;
        }

        this.$confirm('确认删除？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/weiYan/deleteWeiYan", {id: id})
            .then((res) => {
              this.$message({
                type: 'success',
                message: '删除成功!'
              });
              this.getNews();
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消删除!'
          });
        });
      },
      submitWeiYan(content) {
        let weiYan = {
          content: content,
          createTime: this.newsTime,
          source: this.article.id
        };

        this.$http.post(this.$constant.baseURL + "/weiYan/saveNews", weiYan)
          .then((res) => {
            this.weiYanDialogVisible = false;
            this.newsTime = "";
            this.getNews();
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      getNews() {
        this.$http.post(this.$constant.baseURL + "/weiYan/listNews", {
          current: 1,
          size: 9999,
          source: this.article.id
        })
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              res.data.records.forEach(c => {
                c.content = c.content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
                c.content = c.content.replace(/\n/g, '<br/>');
                c.content = this.$common.faceReg(c.content);
                c.content = this.$common.pictureReg(c.content);
              });
              this.treeHoleList = res.data.records;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      onScrollPage() {
        this.scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
        if (this.scrollTop < (window.innerHeight / 4)) {
          $(".toc").css("top", window.innerHeight / 4);
        } else {
          $(".toc").css("top", "90px");
        }
      },
      getTocbot() {
        let script = document.createElement('script');
        script.type = 'text/javascript';
        script.src = this.$constant.tocbot;
        document.getElementsByTagName('head')[0].appendChild(script);

        script.onload = function () {
          tocbot.init({
            tocSelector: '#toc',
            contentSelector: '.entry-content',
            headingSelector: 'h1, h2, h3, h4, h5',
            scrollSmooth: true,
            fixedSidebarOffset: 'auto',
            scrollSmoothOffset: -100,
            hasInnerContainers: false
          });
        }
        if (this.$common.mobile()) {
          $(".toc").css("display", "none");
        }
      },
      addId() {
        let headings = $(".entry-content").find("h1, h2, h3, h4, h5, h6");
        headings.attr('id', (i, id) => id || 'toc-' + i);
      },
      getArticleMeta() {
        this.isLoadingMeta = true;
        const timeout = setTimeout(() => {
          if (this.isLoadingMeta) {
            console.warn('获取文章元标签超时，停止等待');
            this.isLoadingMeta = false;
            this.setDefaultMetaTags();
          }
        }, 3000);
        
        // 使用带noCount参数的API，避免增加热度
        this.$http.get(this.$constant.baseURL + "/article/getArticleByIdNoCount", {id: this.id})
          .then((articleRes) => {
            if (articleRes.code === 200 && articleRes.data) {
              // 文章信息获取成功后再获取SEO元数据
              axios.get(this.$constant.pythonBaseURL + `/python/seo/getArticleMeta?id=${this.id}&lang=${this.currentLang}`)
                .then((res) => {
                  clearTimeout(timeout);
                  this.isLoadingMeta = false;
                  
                  if (res.data && res.data.code === 200 && res.data.data) {
                    this.metaTags = res.data.data;
                    this.updateMetaTags();
                  } else {
                    console.error('获取文章元标签失败, 服务返回错误:', res.data ? (res.data.message || '未知错误') : '返回数据为空');
                    this.setDefaultMetaTags();
                  }
                })
                .catch((error) => {
                  clearTimeout(timeout);
                  this.isLoadingMeta = false;
                  console.error('获取文章元标签失败:', error);
                  
                  // 添加简单的自动重试，最多重试2次
                  if (!this.metaTagRetryCount || this.metaTagRetryCount < 2) {
                    this.metaTagRetryCount = (this.metaTagRetryCount || 0) + 1;
                    console.log(`尝试第${this.metaTagRetryCount}次重新获取元标签...`);
                    setTimeout(() => {
                      this.getArticleMeta();
                    }, 1500); // 1.5秒后重试
                  } else {
                    // 重试失败，使用默认元标签
                    this.setDefaultMetaTags();
                  }
                });
            } else {
              clearTimeout(timeout);
              this.isLoadingMeta = false;
              console.error('获取文章信息失败，无法获取元标签');
              this.setDefaultMetaTags();
            }
          })
          .catch((error) => {
            clearTimeout(timeout);
            this.isLoadingMeta = false;
            console.error('获取文章信息失败:', error);
            this.setDefaultMetaTags();
          });
      },
      setDefaultMetaTags() {
        if (this.article) {
          this.metaTags = {
            title: this.article.articleTitle || 'Poetize博客',
            description: this.article.articleTitle ? (this.article.articleTitle + ' - Poetize博客') : 'Poetize博客',
            keywords: 'Poetize,博客,个人网站',
            author: this.article.username || 'Admin',
            'og:url': window.location.href,
            'og:image': this.article.articleCover || '',
            'twitter:card': 'summary',
            'article:published_time': this.article.createTime || '',
            'article:modified_time': this.article.updateTime || ''
          };
          this.updateMetaTags();
        }
      },
      updateMetaTags() {
        if (!this.metaTags) return;
        
        // 不再设置document.title，保持网站统一标题
        // document.title = this.metaTags.title;
        // window.OriginTitile = this.metaTags.title;
        
        document.querySelectorAll('meta[data-vue-meta="true"]').forEach(el => el.remove());
        
        const addMetaTag = (name, content, isProperty = false) => {
          if (!content) return;
          
          const meta = document.createElement('meta');
          if (isProperty) {
            meta.setAttribute('property', name);
          } else {
            meta.setAttribute('name', name);
          }
          meta.setAttribute('content', content);
          meta.setAttribute('data-vue-meta', 'true');
          document.head.appendChild(meta);
        };
        
        addMetaTag('description', this.metaTags.description);
        addMetaTag('keywords', this.metaTags.keywords);
        addMetaTag('author', this.metaTags.author);
        addMetaTag('og:title', this.metaTags.title, true);
        addMetaTag('og:description', this.metaTags.description, true);
        addMetaTag('og:type', 'article', true);
        addMetaTag('og:url', this.metaTags['og:url'], true);
        addMetaTag('og:image', this.metaTags['og:image'], true);
        addMetaTag('twitter:card', this.metaTags['twitter:card']);
        addMetaTag('twitter:title', this.metaTags.title);
        addMetaTag('twitter:description', this.metaTags.description);
        addMetaTag('twitter:image', this.metaTags['twitter:image']);
        addMetaTag('article:published_time', this.metaTags['article:published_time'], true);
        addMetaTag('article:modified_time', this.metaTags['article:modified_time'], true);
      },
      getArticle(password) {
        this.$http.get(this.$constant.baseURL + "/article/getArticleById", {id: this.id, password: password})
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.article = res.data;
              this.getNews();
              
              // 重置翻译内容，防止缓存影响
              this.translatedTitle = '';
              this.translatedContent = '';
              
              const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
              this.articleContentHtml = md.render(this.article.articleContent);
              this.$nextTick(() => {
                this.$common.imgShow(".entry-content img");
                this.highlight();
                this.addId();
                this.getTocbot();
                this.getArticleMeta();
                
                // 如果当前语言为英文且没有获取过翻译，获取翻译
                if (this.currentLang === 'en') {
                  console.log('加载文章后检测到英文模式，获取翻译');
                  this.fetchTranslation();
                }
              });
              if (!this.$common.isEmpty(password)) {
                localStorage.setItem("article_password_" + this.id, password);
              }
              this.showPasswordDialog = false;
              if (!this.$common.isEmpty(this.$store.state.currentUser) && !this.$common.isEmpty(this.$store.state.currentUser.subscribe)) {
                this.subscribe = JSON.parse(this.$store.state.currentUser.subscribe).includes(this.article.labelId);
              }
            }
          })
          .catch((error) => {
            if ("密码错误" === error.message.substr(0, 4)) {
              if (!this.$common.isEmpty(password)) {
                localStorage.removeItem("article_password_" + this.id);
                this.$message({
                  message: "密码错误，请重新输入！",
                  type: "error",
                  customClass: "message-index"
                });
              }
              this.tips = error.message.substr(4);
              this.showPasswordDialog = true;
            } else {
              this.$message({
                message: error.message,
                type: "error",
                customClass: "message-index"
              });
              this.tips = error.message;
              this.showPasswordDialog = true;
            }
          });
      },
      highlight() {
        let attributes = {
          autocomplete: "off",
          autocorrect: "off",
          autocapitalize: "off",
          spellcheck: "false",
          contenteditable: "false"
        };

        $("pre").each(function (i, item) {
          let preCode = $(item).children("code");
          let classNameStr = preCode[0].className;
          let classNameArr = classNameStr.split(" ");

          let lang = "";
          classNameArr.some(function (className) {
            if (className.indexOf("language-") > -1) {
              lang = className.substring(className.indexOf("-") + 1, className.length);
              return true;
            }
          });

          let language = hljs.getLanguage(lang.toLowerCase());
          if (language === undefined) {
            let autoLanguage = hljs.highlightAuto(preCode.text());
            preCode.removeClass("language-" + lang);
            lang = autoLanguage.language;
            if (lang === undefined) {
              lang = "java";
            }
            preCode.addClass("language-" + lang);
          } else {
            lang = language.name;
          }

          $(item).addClass("highlight-wrap");
          $(item).attr(attributes);
          preCode.attr("data-rel", lang.toUpperCase()).addClass(lang.toLowerCase());
          hljs.highlightBlock(preCode[0]);
          hljs.lineNumbersBlock(preCode[0]);
        });

        $("pre code").each(function (i, block) {
          $(block).attr({
            id: "hljs-" + i,
          });

          $(block).after(
            '<a class="copy-code" href="javascript:" data-clipboard-target="#hljs-' +
            i +
            '"><i class="fa fa-clipboard" aria-hidden="true"></i></a>'
          );
          new ClipboardJS(".copy-code");
        });

        if ($(".entry-content").children("table").length > 0) {
          $(".entry-content")
            .children("table")
            .wrap("<div class='table-wrapper'></div>");
        }
      },
      switchLanguage(lang) {
        if (this.currentLang === lang) return;
        
        this.currentLang = lang;
        
        // 保存用户语言偏好
        localStorage.setItem('preferredLanguage', lang);
        
        // 更新URL参数，不刷新页面
        this.updateUrlWithLanguage(lang);
        
        // 设置HTML元素的lang属性
        document.documentElement.setAttribute('lang', lang);
        
        if (lang === 'en') {
          // 如果已有翻译内容，直接显示
          if (this.translatedContent) {
            // 强制更新显示翻译内容
            const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
            this.articleContentHtml = md.render(this.translatedContent);
            
            // 重新应用文章内容处理
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.addId();
              this.getTocbot();
            });
          } else {
            // 没有翻译内容，获取翻译
            this.fetchTranslation();
          }
        } else if (lang === 'zh') {
          // 切换到中文，确保显示原始内容
          const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
          this.articleContentHtml = md.render(this.article.articleContent);
          
          // 重新应用文章内容处理
          this.$nextTick(() => {
            this.$common.imgShow(".entry-content img");
            this.highlight();
            this.addId();
            this.getTocbot();
          });
        }
      },
      async fetchTranslation() {
        if (!this.article || !this.article.id) {
          console.warn('无法获取翻译：文章ID不存在');
          return;
        }
        
        console.log('开始获取翻译，文章ID:', this.article.id);
        this.isLoading = true;
        try {
          const response = await this.$http.get(this.$constant.baseURL + "/article/getTranslation", {
            id: this.article.id,
            language: "en"
          });
          
          if (response.code === 200 && response.data) {
            console.log('获取翻译成功');
            this.translatedTitle = response.data.title;
            this.translatedContent = response.data.content;
            
            // 更新文章内容显示
            if (this.currentLang === 'en') {
              console.log('当前为英文模式，更新显示翻译内容');
              // 使用与原文相同的渲染方法
              const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
              this.articleContentHtml = md.render(this.translatedContent);
              
              // 重新应用文章内容处理
              this.$nextTick(() => {
                this.$common.imgShow(".entry-content img");
                this.highlight();
                this.addId();
                this.getTocbot();
              });
            } else {
              console.log('当前非英文模式，翻译内容已缓存但不显示');
            }
          } else {
            console.error('获取翻译失败，服务器返回:', response);
            this.$message.error('获取翻译失败');
          }
        } catch (error) {
          console.error('Translation error:', error);
          this.$message.error('获取翻译失败');
        } finally {
          this.isLoading = false;
        }
      },
      updateUrlWithLanguage(lang) {
        // 无论是什么语言，都在URL中添加对应的lang参数，使用replaceState而不是pushState，避免触发路由监听
        const url = new URL(window.location);
        url.searchParams.set('lang', lang);
        window.history.replaceState({}, '', url);
      },
      /**
       * 检查是否有临时保存的评论
       */
      checkTempComment() {
        const articleId = this.id;
        const tempCommentKey = `tempComment_${articleId}`;
        
        try {
          const savedComment = localStorage.getItem(tempCommentKey);
          if (savedComment) {
            const commentData = JSON.parse(savedComment);
            
            // 检查是否过期(24小时)
            const now = Date.now();
            const commentAge = now - commentData.timestamp;
            
            if (commentAge < 24 * 60 * 60 * 1000) {
              this.tempComment = commentData.content;
              
              // 延迟一点时间确保评论组件已加载
              setTimeout(() => {
                // 使用事件总线将评论内容发送到评论框组件
                this.$bus.$emit('restore-comment', this.tempComment);
                
                // 提示用户
                this.$message({
                  message: "已恢复您之前的评论内容",
                  type: "success"
                });
                
                // 滚动到评论区
                this.$nextTick(() => {
                  const commentElement = document.querySelector('.comment-head');
                  if (commentElement) {
                    commentElement.scrollIntoView({ behavior: 'smooth' });
                  }
                });
                
                // 清除临时评论
                localStorage.removeItem(tempCommentKey);
              }, 500);
            } else {
              // 过期则删除
              localStorage.removeItem(tempCommentKey);
            }
          }
        } catch (error) {
          console.error('恢复评论出错:', error);
          localStorage.removeItem(tempCommentKey);
        }
      }
    }
  }
</script>

<style scoped>

  .article-head {
    height: 40vh;
    position: relative;
  }

  .article-image::before {
    position: absolute;
    width: 100%;
    height: 100%;
    background-color: var(--miniMask);
    content: "";
  }

  .article-info-container {
    position: absolute;
    bottom: 15px;
    left: 20%;
    color: var(--white);
  }

  .article-info-news {
    position: absolute;
    bottom: 10px;
    right: 20%;
    cursor: pointer;
    animation: scale 1s ease-in-out infinite;
  }

  .article-title {
    font-size: 28px;
    margin-bottom: 15px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    word-break: break-word;
  }

  .article-info {
    font-size: 14px;
    user-select: none;
  }

  .article-info i {
    margin-right: 6px;
  }

  .article-info span:not(:last-child) {
    margin-right: 5px;
  }

  .article-container {
    max-width: 800px;
    margin: 0 auto;
    padding: 40px 20px;
  }

  .article-update-time {
    color: var(--greyFont);
    font-size: 12px;
    margin: 20px 0;
    user-select: none;
  }

  blockquote {
    line-height: 2;
    border-left: 0.2rem solid var(--blue);
    padding: 10px 1rem;
    background-color: var(--azure);
    border-radius: 4px;
    margin: 0 0 40px 0;
    user-select: none;
    color: var(--black);
  }

  .article-sort {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 20px;
  }

  .article-sort span {
    padding: 3px 10px;
    background-color: var(--themeBackground);
    border-radius: 5px;
    font-size: 14px;
    color: var(--white);
    transition: all 0.3s;
    margin-right: 25px;
    cursor: pointer;
    user-select: none;
  }

  .article-sort span:hover {
    background-color: var(--red);
  }

  .article-like {
    color: var(--red) !important;
  }

  .article-like-icon {
    font-size: 60px;
    cursor: pointer;
    color: var(--greyFont);
    transition: all 0.5s;
    border-radius: 50%;
    margin-bottom: 20px;
  }

  .article-like-icon:hover {
    transform: rotate(360deg);
  }

  .process-wrap {
    margin: 0 0 40px;
  }

  .process-wrap hr {
    position: relative;
    margin: 10px auto 60px;
    border: 2px dashed var(--lightGreen);
    overflow: visible;
  }

  .process-wrap hr:before {
    position: absolute;
    top: -14px;
    left: 5%;
    color: var(--lightGreen);
    content: '❄';
    font-size: 30px;
    line-height: 1;
    transition: all 1s ease-in-out;
  }

  .process-wrap hr:hover:before {
    left: calc(95% - 20px);
  }

  .process-wrap >>> .el-collapse-item__header {
    border-bottom: unset;
    font-size: 20px;
    background-color: var(--background);
    color: var(--lightGreen);
  }

  .process-wrap >>> .el-collapse-item__wrap {
    background-color: var(--background);
  }

  .process-wrap .el-collapse {
    border-top: unset;
    border-bottom: unset;
  }

  .process-wrap >>> .el-collapse-item__wrap {
    border-bottom: unset;
  }

  .password-content {
    font-size: 13px;
    color: var(--maxGreyFont);
    line-height: 1.5;
  }

  #toc-button {
    position: fixed;
    right: 3vh;
    bottom: 8vh;
    animation: slide-bottom 0.5s ease-in-out both;
    z-index: 100;
    cursor: pointer;
    font-size: 23px;
    width: 30px;
  }

  #toc-button:hover {
    color: var(--themeBackground);
  }

  .copyright-container {
    color: var(--black);
    line-height: 2.5;
    padding: 0 30px 10px;
    font-size: 16px;
  }

  @media screen and (max-width: 700px) {
    .article-info-container {
      left: 20px;
      max-width: 320px;
    }

    .article-info-news {
      right: 20px;
    }
  }

  @media screen and (max-width: 400px) {
    #toc-button {
      right: 0.5vh;
    }
  }

  /* 添加语言切换按钮样式 */
  .article-language-switch {
    margin-bottom: 20px;
    z-index: 10;
  }
  
  .article-language-switch .el-button-group {
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.15);
    border-radius: 4px;
    overflow: hidden;
  }
  
  .article-language-switch .el-button {
    padding: 8px 15px;
    font-weight: 500;
    transition: all 0.3s;
  }
  
  .article-language-switch .el-button--primary {
    background-color: var(--themeBackground);
    border-color: var(--themeBackground);
  }
</style>
