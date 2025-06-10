<template>
  <div>
    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        基础信息
      </el-tag>
      <el-form :model="webInfo" :rules="rules" ref="ruleForm" label-width="100px"
               class="demo-ruleForm">
        <el-form-item label="网站名称" prop="webName">
          <el-input v-model="webInfo.webName"></el-input>
        </el-form-item>

        <el-form-item label="网站标题" prop="webTitle">
          <el-input v-model="webInfo.webTitle"></el-input>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-switch @change="changeWebStatus" v-model="webInfo.status"></el-switch>
        </el-form-item>

        <el-form-item label="看板娘" prop="enableWaifu">
          <el-switch @change="handleWaifuChange" v-model="webInfo.enableWaifu"></el-switch>
        </el-form-item>

        <!-- 导航栏配置 -->
        <el-form-item label="导航栏配置">
          <el-popover
            placement="top"
            width="600"
            trigger="hover">
            <div slot="reference" style="cursor: help;">
              <el-input 
                v-model="navConfigText" 
                placeholder="例如：首页,记录,家,百宝箱,留言,联系我">
              </el-input>
            </div>
            <div>
              <p style="margin-top: 0">请填写您希望显示的导航项，<b>以逗号分隔</b>。系统将按照您输入的顺序显示这些导航项。</p>
              <p>可用的导航项包括：首页、记录、家、百宝箱、留言、联系我。</p>
              
              <!-- 导航栏预览 -->
              <div class="nav-preview-section">
                <div class="nav-preview-title">导航栏预览：</div>
                <div class="nav-preview-container">
                  <div 
                    v-for="(item, index) in parsedNavItems" 
                    :key="index"
                    class="nav-item-preview">
                    <span class="nav-item-icon">{{item.icon}}</span>
                    <span class="nav-item-name">{{item.name}}</span>
                    <span v-if="item.type === 'dropdown'" class="nav-item-dropdown">▼</span>
                  </div>
                </div>
              </div>
              
              <div style="margin-top: 15px; display: flex; justify-content: space-between; align-items: center;">
                <el-button size="mini" type="warning" @click="resetToDefaultNav">恢复默认</el-button>
                <el-button size="mini" type="primary" @click="saveNavConfig" :loading="navLoading">单独保存导航栏</el-button>
              </div>
            </div>
          </el-popover>
        </el-form-item>

        <el-form-item label="背景" prop="backgroundImage">
          <div style="display: flex">
            <el-input v-model="webInfo.backgroundImage"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      :preview-src-list="[webInfo.backgroundImage]"
                      :src="webInfo.backgroundImage"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'webBackgroundImage'" style="margin-top: 15px"
                         @addPicture="addBackgroundImage"
                         :maxSize="3"
                         :maxNumber="1"></uploadPicture>
        </el-form-item>

        <el-form-item label="头像" prop="avatar">
          <div style="display: flex">
            <el-input v-model="webInfo.avatar"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      :preview-src-list="[webInfo.avatar]"
                      :src="webInfo.avatar"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'webAvatar'" style="margin-top: 15px" @addPicture="addAvatar"
                         :maxSize="2"
                         :maxNumber="1"></uploadPicture>
        </el-form-item>

        <el-form-item label="页脚" prop="footer">
          <el-input v-model="webInfo.footer" placeholder="页脚信息（可选）"></el-input>
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="webInfo.email" placeholder="联系邮箱（用于隐私政策和侵权联系）"></el-input>
        </el-form-item>

        <el-form-item label="页脚背景" prop="footerBackgroundImage">
          <div style="display: flex">
            <el-input v-model="webInfo.footerBackgroundImage" placeholder="页脚背景图片URL（可选）"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      v-if="webInfo.footerBackgroundImage"
                      :preview-src-list="[webInfo.footerBackgroundImage]"
                      :src="webInfo.footerBackgroundImage"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'footerBackground'" style="margin-top: 15px"
                         @addPicture="addFooterBackgroundImage"
                         :maxSize="3"
                         :maxNumber="1"></uploadPicture>
          
          <!-- 背景图片配置选项 -->
          <div v-if="webInfo.footerBackgroundImage" style="margin-top: 15px;">
            <el-divider content-position="left">背景图片设置</el-divider>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="背景大小" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundSize" placeholder="选择背景大小">
                    <el-option label="覆盖 (cover)" value="cover"></el-option>
                    <el-option label="包含 (contain)" value="contain"></el-option>
                    <el-option label="自动 (auto)" value="auto"></el-option>
                    <el-option label="拉伸 (100% 100%)" value="100% 100%"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="背景位置" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundPosition" placeholder="选择背景位置">
                    <el-option label="居中" value="center center"></el-option>
                    <el-option label="顶部居中" value="center top"></el-option>
                    <el-option label="底部居中" value="center bottom"></el-option>
                    <el-option label="左上角" value="left top"></el-option>
                    <el-option label="右上角" value="right top"></el-option>
                    <el-option label="左下角" value="left bottom"></el-option>
                    <el-option label="右下角" value="right bottom"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重复方式" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundRepeat" placeholder="选择重复方式">
                    <el-option label="不重复" value="no-repeat"></el-option>
                    <el-option label="重复" value="repeat"></el-option>
                    <el-option label="水平重复" value="repeat-x"></el-option>
                    <el-option label="垂直重复" value="repeat-y"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="透明度" label-width="80px">
                  <el-slider v-model="footerBgConfig.opacity" 
                           :min="0" 
                           :max="100" 
                           :step="5"
                           :format-tooltip="val => val + '%'">
                  </el-slider>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="文字阴影" label-width="80px">
                  <el-switch v-model="footerBgConfig.textShadow"></el-switch>
                  <span style="margin-left: 10px; color: #999; font-size: 12px;">增强文字可读性</span>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="遮罩颜色" label-width="80px">
                  <div style="display: flex; align-items: center; gap: 10px;">
                    <el-color-picker v-model="footerBgConfig.maskColor"
                                   :predefine="['#000000', '#1a1a1a', '#333333', '#444444', '#555555', '#666666', '#FFFFFF']"
                                   show-alpha
                                   color-format="rgba">
                    </el-color-picker>
                    <span style="color: #999; font-size: 12px;">调整遮罩颜色和透明度</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="效果预览" label-width="80px">
                  <div style="width: 100px; height: 30px; border: 1px solid #ddd; border-radius: 4px; position: relative; overflow: hidden;">
                    <div style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>
                    <div :style="{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      bottom: 0,
                      background: footerBgConfig.maskColor || 'rgba(0, 0, 0, 0.5)'
                    }"></div>
                    <span style="position: relative; z-index: 10; color: white; font-size: 11px; display: block; text-align: center; line-height: 30px;">样例文字</span>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-form-item>

        <el-form-item label="提示" prop="waifuJson">
          <div style="display: flex">
            <el-input :disabled="disabled" :rows="6" type="textarea" v-model="webInfo.waifuJson"></el-input>
            <i class="el-icon-edit my-icon" @click="disabled = !disabled"></i>
          </div>
        </el-form-item>
      </el-form>
      <div class="myCenter" style="margin-bottom: 22px">
        <el-button type="primary" @click="submitForm('ruleForm')">保存基本信息</el-button>
      </div>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        公告
      </el-tag>
      <el-tag
        :key="i"
        v-for="(notice, i) in notices"
        closable
        :disable-transitions="false"
        @close="handleClose(notices, notice)">
        {{notice}}
      </el-tag>
      <el-input
        class="input-new-tag"
        v-if="inputNoticeVisible"
        v-model="inputNoticeValue"
        ref="saveNoticeInput"
        size="small"
        @keyup.enter.native="handleInputNoticeConfirm"
        @blur="handleInputNoticeConfirm">
      </el-input>
      <el-button v-else class="button-new-tag" size="small" @click="showNoticeInput()">+ 公告</el-button>
      <div class="myCenter" style="margin-bottom: 22px">
        <el-button type="primary" @click="saveNotice()">保存公告</el-button>
      </div>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        随机名称
      </el-tag>
      <el-tag
        :key="i"
        effect="dark"
        v-for="(name, i) in randomName"
        closable
        :disable-transitions="false"
        :type="types[Math.floor(Math.random() * 5)]"
        @close="handleClose(randomName, name)">
        {{name}}
      </el-tag>
      <el-input
        class="input-new-tag"
        v-if="inputRandomNameVisible"
        v-model="inputRandomNameValue"
        ref="saveRandomNameInput"
        size="small"
        @keyup.enter.native="handleInputRandomNameConfirm"
        @blur="handleInputRandomNameConfirm">
      </el-input>
      <el-button v-else class="button-new-tag" size="small" @click="showRandomNameInput">+ 随机名称</el-button>
      <div class="myCenter" style="margin-bottom: 22px">
        <el-button type="primary" @click="saveRandomName()">保存随机名称</el-button>
      </div>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        随机头像
      </el-tag>
      <div :key="i"
           style="display: flex"
           v-for="(avatar, i) in randomAvatar">
        <el-tag
          style="white-space: normal;height: unset"
          closable
          :disable-transitions="false"
          @close="handleClose(randomAvatar, avatar)">
          {{avatar}}
        </el-tag>
        <div>
          <el-image lazy class="table-td-thumb"
                    style="margin: 10px"
                    :preview-src-list="[avatar]"
                    :src="avatar"
                    fit="cover"></el-image>
        </div>
      </div>

      <el-input
        class="input-new-tag"
        v-if="inputRandomAvatarVisible"
        v-model="inputRandomAvatarValue"
        ref="saveRandomAvatarInput"
        size="small"
        @keyup.enter.native="handleInputRandomAvatarConfirm"
        @blur="handleInputRandomAvatarConfirm">
      </el-input>
      <el-button v-else class="button-new-tag" size="small" @click="showRandomAvatarInput">+ 随机头像</el-button>
      <uploadPicture :isAdmin="true" :prefix="'randomAvatar'" style="margin: 10px" @addPicture="addRandomAvatar"
                     :maxSize="1"
                     :maxNumber="5"></uploadPicture>
      <div class="myCenter" style="margin-bottom: 22px">
        <el-button type="primary" @click="saveRandomAvatar()">保存随机头像</el-button>
      </div>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        随机封面
      </el-tag>
      <div :key="i"
           style="display: flex"
           v-for="(cover, i) in randomCover">
        <el-tag
          style="white-space: normal;height: unset"
          closable
          :disable-transitions="false"
          @close="handleClose(randomCover, cover)">
          {{cover}}
        </el-tag>
        <div>
          <el-image lazy class="table-td-thumb"
                    style="margin: 10px"
                    :preview-src-list="[cover]"
                    :src="cover"
                    fit="cover"></el-image>
        </div>
      </div>

      <el-input
        class="input-new-tag"
        v-if="inputRandomCoverVisible"
        v-model="inputRandomCoverValue"
        ref="saveRandomCoverInput"
        size="small"
        @keyup.enter.native="handleInputRandomCoverConfirm"
        @blur="handleInputRandomCoverConfirm">
      </el-input>
      <el-button v-else class="button-new-tag" size="small" @click="showRandomCoverInput">+ 随机封面</el-button>
      <uploadPicture :isAdmin="true" :prefix="'randomCover'" style="margin: 10px" @addPicture="addRandomCover"
                     :maxSize="2"
                     :maxNumber="5"></uploadPicture>
      <div class="myCenter" style="margin-bottom: 40px">
        <el-button type="primary" @click="saveRandomCover()">保存随机封面</el-button>
      </div>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        邮箱配置
      </el-tag>
      
      <!-- 邮箱配置部分 -->
      <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
        
        
        <el-alert
          title="配置说明"
          type="info"
          description="您可以配置多个邮箱，系统将随机选择一个进行邮件发送，提高邮件送达率。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px;">
        </el-alert>
        
        <!-- 加密配置提示 -->
        <el-alert
          title="加密说明"
          type="warning"
          description="注意：端口465必须使用SSL加密；端口587通常使用STARTTLS加密；端口25通常不加密且可能被ISP阻止。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px;">
        </el-alert>
        
        <!-- 响应式表格 -->
        <div class="responsive-table-container">
          <el-table
            :data="emailConfigs"
            border
            style="width: 100%"
            :class="{'mobile-table': isMobileDevice}"
            size="small">
            
            <el-table-column
              prop="smtpHost"
              label="邮箱服务器"
              min-width="150">
          <template slot-scope="scope">
                <el-input v-model="scope.row.host" placeholder="例如: smtp.163.com"></el-input>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="smtpUsername"
              label="邮箱地址"
              min-width="180"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <el-input v-model="scope.row.username" placeholder="例如: example@163.com"></el-input>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="smtpPassword"
              label="授权码"
              min-width="150"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <el-input v-model="scope.row.password" placeholder="授权码" show-password></el-input>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="smtpPort"
              label="端口"
              min-width="80"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <el-input-number v-model="scope.row.port" :min="1" :max="65535" :controls="false" style="width: 100%" @change="(value) => onPortChange(value, scope.row, scope.$index)"></el-input-number>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="senderName"
              label="发件人名称"
              min-width="120"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <el-input v-model="scope.row.senderName" placeholder="发件人显示名称"></el-input>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="ssl"
              label="SSL"
              min-width="80"
              align="center">
          <template slot-scope="scope">
                <div>
                  <el-tooltip content="SSL是一种加密连接方式，端口465必须启用SSL" placement="top">
                    <i class="el-icon-question" style="margin-right: 5px; color: #909399; font-size: 14px;"></i>
                  </el-tooltip>
                  <el-switch v-model="scope.row.useSsl" @change="(value) => onSslChange(value, scope.row, scope.$index)"></el-switch>
                </div>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="starttls"
              label="STARTTLS"
              min-width="100"
              align="center"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <div>
                  <el-tooltip content="STARTTLS是在连接后升级为加密，端口587通常使用STARTTLS" placement="top">
                    <i class="el-icon-question" style="margin-right: 5px; color: #909399; font-size: 14px;"></i>
                  </el-tooltip>
                  <el-switch v-model="scope.row.useStarttls" @change="(value) => onStarttlsChange(value, scope.row, scope.$index)"></el-switch>
                </div>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="auth"
              label="认证"
              min-width="80"
              align="center"
              :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
          <template slot-scope="scope">
                <el-switch v-model="scope.row.auth"></el-switch>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="enabled"
              label="启用"
              min-width="80"
              align="center">
          <template slot-scope="scope">
            <el-switch v-model="scope.row.enabled"></el-switch>
          </template>
        </el-table-column>
            
            <el-table-column
              prop="operation"
              label="操作"
              min-width="180">
          <template slot-scope="scope">
                <div>
                  <el-button 
                    type="text" 
                    size="small" 
                    @click="testEmailConfig(scope.row, scope.$index)">
                    <i class="el-icon-check"></i> 测试
                  </el-button>
                  <el-button 
                    type="text" 
                    size="small"
                    @click="showAdvancedConfig(scope.$index)">
                    <i class="el-icon-setting"></i> 高级
                  </el-button>
                  <el-button 
                    type="text" 
                    size="small"
                    class="delete-btn"
                    @click="deleteEmailConfig(scope.$index)">
                    <i class="el-icon-delete"></i> 删除
                  </el-button>
                </div>
          </template>
        </el-table-column>
      </el-table>
        </div>
        
        <!-- 在移动设备上显示隐藏列的详情面板 -->
        <div v-if="isMobileDevice" class="mobile-view-notice">
          <el-alert
            title="提示"
            type="info"
            description="在移动设备上点击表格行可查看完整信息"
            show-icon
            :closable="false"
            style="margin: 10px 0;">
          </el-alert>
      </div>
        <div style="margin-top: 37px; text-align: center;">
          <el-button type="success" size="small"  @click="addEmailConfig">添加邮箱</el-button>
        </div>
        <div style="margin-top: 10px; margin-bottom: 22px; text-align: center;">
          
          <el-button type="primary" @click="saveEmailConfigs">保存邮箱配置</el-button>
        </div>
        
        <!-- 邮件测试对话框 -->
        <el-dialog title="测试邮件发送" :visible.sync="emailTestDialogVisible" width="500px">
          <el-form :model="emailTestForm" label-width="100px">
            <el-form-item label="测试邮箱">
              <el-input v-model="emailTestForm.testEmail" placeholder="请输入接收测试邮件的邮箱地址"></el-input>
          </el-form-item>
        </el-form>
          <span slot="footer" class="dialog-footer">
            <el-button @click="emailTestDialogVisible = false">取 消</el-button>
            <el-button type="primary" @click="submitTestEmail" :loading="testEmailLoading">发送测试邮件</el-button>
          </span>
      </el-dialog>
      </el-card>
    </div>

    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        第三方登录配置
      </el-tag>
      
      <!-- 第三方登录配置 -->
      <el-card class="box-card third-login-config" shadow="never" style="margin-top: 20px; border: none;">
        <el-alert
          title="配置说明"
          type="info"
          description="您需要在各平台开发者中心创建应用并获取Client ID和Secret。填写后用户可通过相应平台进行第三方登录。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px;">
        </el-alert>
        
        <el-row style="margin-bottom: 20px;">
          <el-col :span="24">
            <el-form label-width="150px">
              <el-form-item label="启用第三方登录">
                <el-switch 
                  v-model="thirdLoginConfig.enable" 
                  @change="handleThirdLoginToggle"
                  active-color="#13ce66"
                  inactive-color="#ff4949">
                </el-switch>
                <span style="margin-left: 10px; color: #909399; font-size: 12px;">
                  {{ thirdLoginConfig.enable ? '已启用' : '已禁用' }}
                </span>
              </el-form-item>
            </el-form>
          </el-col>
        </el-row>

        <div class="platform-cards">
          <!-- GitHub -->
          <el-card shadow="never" class="platform-card github-card" style="border: none;">
            <div class="platform-header">
              <div class="platform-logo">
                <img :src="getPlatformIcon('github')" width="28" height="28" alt="GitHub">
                <span class="platform-name">GitHub</span>
              </div>
              <el-switch
                v-model="thirdLoginConfig.github.enabled"
                @change="handlePlatformToggle('github', $event)"
                active-color="#13ce66"
                inactive-color="#ff4949"
                :disabled="!thirdLoginConfig.enable">
              </el-switch>
            </div>
            
            <div class="platform-form">
              <el-form label-position="top" :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.github.enabled">
                <el-form-item label="Client ID">
                  <el-input 
                    v-model="thirdLoginConfig.github.client_id" 
                    placeholder="请输入Client ID">
                  </el-input>
        </el-form-item>
                
                <el-form-item label="Client Secret">
                  <el-input 
                    v-model="thirdLoginConfig.github.client_secret" 
                    placeholder="请输入Client Secret"
                    show-password>
                  </el-input>
        </el-form-item>
                
                <el-form-item label="回调地址">
                  <el-input 
                    v-model="thirdLoginConfig.github.redirect_uri" 
                    placeholder="请输入回调地址">
                  </el-input>
        </el-form-item>
              </el-form>
            </div>
            
            <div class="platform-actions">
              <el-button 
                type="text" 
                icon="el-icon-link"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.github.enabled"
                @click="openDeveloperCenter('https://github.com/settings/developers')">
                开发者中心
              </el-button>
              <el-button 
                type="text" 
                icon="el-icon-check"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.github.enabled"
                @click="testLogin('github')">
                测试
              </el-button>
            </div>
          </el-card>
          
          <!-- Google -->
          <el-card shadow="never" class="platform-card google-card" style="border: none;">
            <div class="platform-header">
              <div class="platform-logo">
                <img :src="getPlatformIcon('google')" width="28" height="28" alt="Google">
                <span class="platform-name">Google</span>
              </div>
              <el-switch
                v-model="thirdLoginConfig.google.enabled"
                @change="handlePlatformToggle('google', $event)"
                active-color="#13ce66"
                inactive-color="#ff4949"
                :disabled="!thirdLoginConfig.enable">
              </el-switch>
            </div>
            
            <div class="platform-form">
              <el-form label-position="top" :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.google.enabled">
                <el-form-item label="Client ID">
                  <el-input 
                    v-model="thirdLoginConfig.google.client_id" 
                    placeholder="请输入Client ID">
                  </el-input>
                </el-form-item>
                
                <el-form-item label="Client Secret">
                  <el-input 
                    v-model="thirdLoginConfig.google.client_secret" 
                    placeholder="请输入Client Secret"
                    show-password>
                  </el-input>
                </el-form-item>
                
                <el-form-item label="回调地址">
                  <el-input 
                    v-model="thirdLoginConfig.google.redirect_uri" 
                    placeholder="请输入回调地址">
                  </el-input>
        </el-form-item>
      </el-form>
            </div>
            
            <div class="platform-actions">
              <el-button 
                type="text" 
                icon="el-icon-link"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.google.enabled"
                @click="openDeveloperCenter('https://console.cloud.google.com/apis/credentials')">
                开发者中心
              </el-button>
              <el-button 
                type="text" 
                icon="el-icon-check"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.google.enabled"
                @click="testLogin('google')">
                测试
              </el-button>
            </div>
          </el-card>
          
          <!-- Twitter -->
          <el-card shadow="never" class="platform-card twitter-card" style="border: none;">
            <div class="platform-header">
              <div class="platform-logo">
                <img :src="getPlatformIcon('twitter')" width="28" height="28" alt="Twitter">
                <span class="platform-name">Twitter</span>
              </div>
              <el-switch
                v-model="thirdLoginConfig.twitter.enabled"
                @change="handlePlatformToggle('twitter', $event)"
                active-color="#13ce66"
                inactive-color="#ff4949"
                :disabled="!thirdLoginConfig.enable">
              </el-switch>
            </div>
            
            <div class="platform-form">
              <el-form label-position="top" :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.twitter.enabled">
                <el-form-item label="API Key">
                  <el-input 
                    v-model="thirdLoginConfig.twitter.client_key" 
                    placeholder="请输入Twitter API Key">
                  </el-input>
                </el-form-item>
                
                <el-form-item label="API Secret">
                  <el-input 
                    v-model="thirdLoginConfig.twitter.client_secret" 
                    placeholder="请输入Client Secret"
                    show-password>
                  </el-input>
                </el-form-item>
                
                <el-form-item label="回调地址">
                  <el-input 
                    v-model="thirdLoginConfig.twitter.redirect_uri" 
                    placeholder="请输入回调地址">
                  </el-input>
                </el-form-item>
              </el-form>
            </div>
            
            <div class="platform-actions">
              <el-button 
                type="text" 
                icon="el-icon-link"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.twitter.enabled"
                @click="openDeveloperCenter('https://developer.twitter.com/en/portal/dashboard')">
                开发者中心
              </el-button>
              <el-button 
                type="text" 
                icon="el-icon-check"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.twitter.enabled"
                @click="testLogin('twitter')">
                测试
              </el-button>
            </div>
          </el-card>
          
          <!-- Yandex -->
          <el-card shadow="never" class="platform-card yandex-card" style="border: none;">
            <div class="platform-header">
              <div class="platform-logo">
                <img :src="getPlatformIcon('yandex')" width="28" height="28" alt="Yandex">
                <span class="platform-name">Yandex</span>
              </div>
              <el-switch
                v-model="thirdLoginConfig.yandex.enabled"
                @change="handlePlatformToggle('yandex', $event)"
                active-color="#13ce66"
                inactive-color="#ff4949"
                :disabled="!thirdLoginConfig.enable">
              </el-switch>
            </div>
            
            <div class="platform-form">
              <el-form label-position="top" :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.yandex.enabled">
                <el-form-item label="Client ID">
                  <el-input 
                    v-model="thirdLoginConfig.yandex.client_id" 
                    placeholder="请输入Client ID">
                  </el-input>
                </el-form-item>
                
                <el-form-item label="Client Secret">
                  <el-input 
                    v-model="thirdLoginConfig.yandex.client_secret" 
                    placeholder="请输入Client Secret"
                    show-password>
                  </el-input>
                </el-form-item>
                
                <el-form-item label="回调地址">
                  <el-input 
                    v-model="thirdLoginConfig.yandex.redirect_uri" 
                    placeholder="请输入回调地址">
                  </el-input>
                </el-form-item>
              </el-form>
            </div>
            
            <div class="platform-actions">
              <el-button 
                type="text" 
                icon="el-icon-link"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.yandex.enabled"
                @click="openDeveloperCenter('https://oauth.yandex.com/')">
                开发者中心
              </el-button>
              <el-button 
                type="text" 
                icon="el-icon-check"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.yandex.enabled"
                @click="testLogin('yandex')">
                测试
              </el-button>
            </div>
          </el-card>
          
          <!-- Gitee -->
          <el-card shadow="never" class="platform-card gitee-card" style="border: none;">
            <div class="platform-header">
              <div class="platform-logo">
                <img :src="getPlatformIcon('gitee')" width="28" height="28" alt="Gitee">
                <span class="platform-name">Gitee</span>
              </div>
              <el-switch
                v-model="thirdLoginConfig.gitee.enabled"
                @change="handlePlatformToggle('gitee', $event)"
                active-color="#13ce66"
                inactive-color="#ff4949"
                :disabled="!thirdLoginConfig.enable">
              </el-switch>
            </div>
            
            <div class="platform-form">
              <el-form label-position="top" :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.gitee.enabled">
                <el-form-item label="Client ID">
                  <el-input 
                    v-model="thirdLoginConfig.gitee.client_id" 
                    placeholder="请输入Client ID">
                  </el-input>
                </el-form-item>
                
                <el-form-item label="Client Secret">
                  <el-input 
                    v-model="thirdLoginConfig.gitee.client_secret" 
                    placeholder="请输入Client Secret"
                    show-password>
                  </el-input>
                </el-form-item>
                
                <el-form-item label="回调地址">
                  <el-input 
                    v-model="thirdLoginConfig.gitee.redirect_uri" 
                    placeholder="请输入回调地址">
                  </el-input>
                </el-form-item>
              </el-form>
            </div>
            
            <div class="platform-actions">
              <el-button 
                type="text" 
                icon="el-icon-link"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.gitee.enabled"
                @click="openDeveloperCenter('https://gitee.com/settings/applications')">
                开发者中心
              </el-button>
              <el-button 
                type="text" 
                icon="el-icon-check"
                :disabled="!thirdLoginConfig.enable || !thirdLoginConfig.gitee.enabled"
                @click="testLogin('gitee')">
                测试
              </el-button>
            </div>
          </el-card>
        </div>

        <div class="form-tip" style="margin-top: 15px; font-size: 13px; color: #909399;">
          * 回调地址通常为 http://你的域名/callback/{平台标识}
        </div>

        <div style="margin-top: 20px;margin-bottom: 22px; text-align: center;">
          <el-button type="primary" @click="saveThirdLoginConfig" :loading="loading">保存第三方登录配置</el-button>
        </div>
      </el-card>
    </div>

    <!-- 智能验证码配置 -->
    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        智能验证码配置
      </el-tag>
      <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
        <el-alert
          title="智能验证码说明"
          type="info"
          description="智能验证码可以有效防止机器人自动化操作，提高网站安全性。根据设备类型自动选择勾选验证码或滑动验证码，PC端使用简单复选框验证，移动端使用滑动验证码，有效防止机器人自动化操作。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px;">
        </el-alert>
        <el-row style="margin-bottom: 20px;">
          <el-col :span="24">
            <el-form label-width="150px">
              <el-form-item label="启用智能验证码">
                <el-switch 
                  v-model="captchaConfig.enable" 
                  @change="handleCaptchaToggle">
                </el-switch>
                <span style="margin-left: 10px; color: #909399; font-size: 12px;">
                  {{ captchaConfig.enable ? '已启用' : '已禁用' }}
                </span>
              </el-form-item>
            </el-form>
          </el-col>
        </el-row>
        
        <el-divider content-position="center">应用场景</el-divider>
        
        <el-row :gutter="20" style="margin-bottom: 20px;">
          <el-col :span="12">
            <el-card 
              shadow="hover" 
              :class="{ 'captcha-card-disabled': !captchaConfig.enable }"
              :body-style="{ padding: '15px' }">
              <div slot="header" class="clearfix">
                <span>登录验证</span>
                <el-switch 
                  v-model="captchaConfig.login" 
                  :disabled="!captchaConfig.enable"
                  style="float: right;">
                </el-switch>
              </div>
              <div>
                <p>在用户登录时启用验证码</p>
                <p style="color: #909399; font-size: 12px;">推荐启用，可以防止暴力破解攻击</p>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card 
              shadow="hover" 
              :class="{ 'captcha-card-disabled': !captchaConfig.enable }"
              :body-style="{ padding: '15px' }">
              <div slot="header" class="clearfix">
                <span>注册验证</span>
                <el-switch 
                  v-model="captchaConfig.register" 
                  :disabled="!captchaConfig.enable"
                  style="float: right;">
                </el-switch>
              </div>
              <div>
                <p>在用户注册时启用验证码</p>
                <p style="color: #909399; font-size: 12px;">推荐启用，可以防止批量注册机器人</p>
              </div>
            </el-card>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="margin-bottom: 20px;">
          <el-col :span="12">
            <el-card 
              shadow="hover" 
              :class="{ 'captcha-card-disabled': !captchaConfig.enable }"
              :body-style="{ padding: '15px' }">
              <div slot="header" class="clearfix">
                <span>评论验证</span>
                <el-switch 
                  v-model="captchaConfig.comment" 
                  :disabled="!captchaConfig.enable"
                  style="float: right;">
                </el-switch>
              </div>
              <div>
                <p>在用户发表评论时启用验证码</p>
                <p style="color: #909399; font-size: 12px;">可以减少垃圾评论</p>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card 
              shadow="hover" 
              :class="{ 'captcha-card-disabled': !captchaConfig.enable }"
              :body-style="{ padding: '15px' }">
              <div slot="header" class="clearfix">
                <span>密码重置验证</span>
                <el-switch 
                  v-model="captchaConfig.reset_password" 
                  :disabled="!captchaConfig.enable"
                  style="float: right;">
                </el-switch>
              </div>
              <div>
                <p>在用户重置密码时启用验证码</p>
                <p style="color: #909399; font-size: 12px;">推荐启用，可以提高账号安全性</p>
              </div>
            </el-card>
          </el-col>
        </el-row>
        
        <el-divider content-position="center">设备适配</el-divider>
        
        <el-row :gutter="20" style="margin-bottom: 20px;">
          <el-col :span="12">
            <el-form label-width="200px">
              <el-form-item label="屏幕宽度阈值(px)">
                <el-tooltip content="小于此宽度使用滑动验证码，大于等于此宽度使用勾选验证码" placement="top">
                  <el-input-number 
                    v-model="captchaConfig.screenSizeThreshold"
                    :min="320"
                    :max="1200"
                    :step="1"
                    :disabled="!captchaConfig.enable">
                  </el-input-number>
                </el-tooltip>
              </el-form-item>
            </el-form>
          </el-col>
          <el-col :span="12">
            <el-form label-width="200px">
              <el-form-item label="移动设备强制使用滑动验证">
                <el-tooltip content="在触摸设备上强制使用滑动验证码，无论屏幕大小" placement="top">
                  <el-switch 
                    v-model="captchaConfig.forceSlideForMobile"
                    :disabled="!captchaConfig.enable">
                  </el-switch>
                </el-tooltip>
              </el-form-item>
            </el-form>
          </el-col>
        </el-row>
        
        <el-divider content-position="center">验证码参数</el-divider>
        
        <el-tabs v-model="activeCaptchaTab" :disabled="!captchaConfig.enable">
          <el-tab-pane label="勾选验证参数" name="checkbox">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form label-width="200px">
                  <el-form-item label="轨迹敏感度阈值">
                    <el-tooltip content="直线率超过此值将被判定为机器人，值越小越严格" placement="top">
                      <el-slider 
                        v-model="captchaConfig.checkbox.trackSensitivity" 
                        :min="0.8" 
                        :max="0.99" 
                        :step="0.01"
                        :format-tooltip="value => value ? value.toFixed(2) : '0'"
                        :disabled="!captchaConfig.enable">
                      </el-slider>
                    </el-tooltip>
                  </el-form-item>
                </el-form>
              </el-col>
              <el-col :span="12">
                <el-form label-width="200px">
                  <el-form-item label="最少轨迹点数">
                    <el-tooltip content="鼠标轨迹至少需要记录的点数，越多越严格" placement="top">
                      <el-input-number 
                        v-model="captchaConfig.checkbox.minTrackPoints"
                        :min="1"
                        :max="10"
                        :step="1"
                        :disabled="!captchaConfig.enable">
                      </el-input-number>
                    </el-tooltip>
                  </el-form-item>
                </el-form>
              </el-col>
            </el-row>
          </el-tab-pane>
          
          <el-tab-pane label="滑动验证参数" name="slide">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form label-width="200px">
                  <el-form-item label="精确度">
                    <el-tooltip content="滑动验证的精确度，值越小越精确" placement="top">
                      <el-input-number 
                        v-model="captchaConfig.slide.accuracy"
                        :min="1"
                        :max="10"
                        :step="1"
                        :disabled="!captchaConfig.enable">
                      </el-input-number>
                    </el-tooltip>
                  </el-form-item>
                </el-form>
              </el-col>
              <el-col :span="12">
                <el-form label-width="200px">
                  <el-form-item label="成功阈值">
                    <el-tooltip content="滑动到最大距离的比例视为成功，值越大越难" placement="top">
                      <el-slider 
                        v-model="captchaConfig.slide.successThreshold" 
                        :min="0.8" 
                        :max="0.99" 
                        :step="0.01"
                        :format-tooltip="value => value ? (value * 100).toFixed(0) + '%' : '0%'"
                        :disabled="!captchaConfig.enable">
                      </el-slider>
                    </el-tooltip>
                  </el-form-item>
                </el-form>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
        
        <div style="margin-top: 20px;margin-bottom: 22px; text-align: center;">
          <el-button type="primary" @click="saveCaptchaConfig" :loading="captchaLoading">保存智能验证码配置</el-button>
        </div>
      </el-card>
    </div>

    <!-- 添加API配置 -->
    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        API 配置
      </el-tag>
      
      <!-- API 配置部分 -->
      <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
        <el-alert
          title="API配置说明"
          type="info"
          description="启用API后，您可以通过API接口来创建和发布文章，无需打开网站。请妥善保管您的API密钥，不要泄露给他人。"
          show-icon
          :closable="false"
          style="margin-bottom: 20px;">
        </el-alert>
        
        <!-- API开关 -->
        <div style="margin-bottom: 20px;">
          <el-form :model="apiConfig" label-width="120px">
            <el-form-item label="启用API">
              <el-switch 
                v-model="apiConfig.enabled" 
                @change="handleApiToggle"
                active-color="#13ce66"
                inactive-color="#ff4949">
              </el-switch>
            </el-form-item>
            
            <el-form-item v-if="apiConfig.enabled" label="API密钥">
              <div style="display: flex; align-items: center;">
                <el-input 
                  v-model="apiConfig.apiKey" 
                  placeholder="API密钥" 
                  :disabled="true"
                  style="width: 350px;">
                </el-input>
                <el-button 
                  type="primary" 
                  size="small" 
                  style="margin-left: 10px;"
                  @click="regenerateApiKey">
                  重新生成
                </el-button>
              </div>
            </el-form-item>
            
            <el-form-item v-if="apiConfig.enabled" label="API端点">
              <div>
                <p style="margin: 5px 0; color: #606266;">文章创建API:</p>
                <el-input
                  :value="$constant.baseURL + '/api/article/create'"
                  :disabled="true"
                  style="width: 450px; margin-bottom: 10px;">
                </el-input>
              </div>
            </el-form-item>
            
            <el-form-item v-if="apiConfig.enabled" label="API文档">
              <el-collapse>
                <el-collapse-item title="API调用概述" name="0">
                  <div style="padding: 10px;">
                    <p><strong>API认证:</strong></p>
                    <p>所有API请求都需要在请求头中添加<code>X-API-KEY</code>字段，值为API密钥。</p>
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,       // 0表示成功，非0表示错误
  "message": null, // 错误信息，成功时为null
  "data": { ... }  // 响应数据
}
                    </pre>
                  </div>
                </el-collapse-item>
                <el-collapse-item title="创建文章 API" name="1">
                  <div style="padding: 10px;">
                    <p><strong>请求格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/create
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "title": "文章标题",
  "content": "文章内容，支持Markdown格式",
  "cover": "封面图片URL(可选)",
  "sortName": "分类名称(将自动创建不存在的分类)",
  "labelName": "标签名称(将自动创建不存在的标签)",
  "summary": "文章摘要(可选)",
  "password": "文章密码(可选)",
  "viewStatus": true,  // 是否可见
  "commentStatus": true,  // 是否允许评论
  "submitToSearchEngine": true  // 是否推送至搜索引擎
}
                    </pre>
                    
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,       // 0表示成功
  "message": null,
  "data": {
    "id": 123      // 创建的文章ID
  }
}
                    </pre>
                  </div>
                </el-collapse-item>
                <el-collapse-item title="获取文章列表 API" name="2">
                  <div style="padding: 10px;">
                    <p><strong>请求格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article/list?current=1&size=10&sortId=1&labelId=1&searchKey=关键词
X-API-KEY: {{apiConfig.apiKey}}
                    </pre>
                    <p><strong>参数说明:</strong></p>
                    <ul>
                      <li><code>current</code>: 当前页码，从1开始，默认为1</li>
                      <li><code>size</code>: 每页大小，默认为10</li>
                      <li><code>sortId</code>: 分类ID，可选</li>
                      <li><code>labelId</code>: 标签ID，可选</li>
                      <li><code>searchKey</code>: 搜索关键词，可选</li>
                    </ul>
                    
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,
  "message": null,
  "data": {
    "records": [
      {
        "id": 123,
        "articleTitle": "文章标题",
        "articleContent": "文章内容摘要...",
        "articleCover": "图片URL",
        "viewCount": 100,
        "likeCount": 10,
        "commentCount": 5,
        "createTime": "2023-04-01 12:00:00",
        "sort": { "id": 1, "sortName": "分类名称" },
        "label": { "id": 1, "labelName": "标签名称" }
      }
      // ...更多文章记录
    ],
    "current": 1,
    "size": 10,
    "total": 42,
    "pages": 5
  }
}
                    </pre>
                  </div>
                </el-collapse-item>
                <el-collapse-item title="获取文章详情 API" name="3">
                  <div style="padding: 10px;">
                    <p><strong>请求格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article/123
X-API-KEY: {{apiConfig.apiKey}}
                    </pre>
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,
  "message": null,
  "data": {
    "id": 123,
    "articleTitle": "文章标题",
    "articleContent": "完整文章内容，包括Markdown格式",
    "articleCover": "图片URL",
    "viewCount": 100,
    "likeCount": 10,
    "commentStatus": true,
    "recommendStatus": false,
    "viewStatus": true,
    "createTime": "2023-04-01 12:00:00",
    "updateTime": "2023-04-02 14:30:00",
    "sortId": 1,
    "labelId": 1,
    "sortName": "分类名称",
    "labelName": "标签名称",
    "sort": { "id": 1, "sortName": "分类名称" },
    "label": { "id": 1, "labelName": "标签名称" }
  }
}
                    </pre>
                  </div>
                </el-collapse-item>
                <el-collapse-item title="获取分类列表 API" name="4">
                  <div style="padding: 10px;">
                    <p><strong>请求格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/categories
X-API-KEY: {{apiConfig.apiKey}}
                    </pre>
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,
  "message": null,
  "data": [
    {
      "id": 1,
      "sortName": "技术文章",
      "sortDescription": "技术类文章",
      "sortType": 0,
      "priority": 1
    },
    // ...更多分类
  ]
}
                    </pre>
                  </div>
                </el-collapse-item>
                <el-collapse-item title="获取标签列表 API" name="5">
                  <div style="padding: 10px;">
                    <p><strong>请求格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/tags
X-API-KEY: {{apiConfig.apiKey}}
                    </pre>
                    <p><strong>响应格式:</strong></p>
                    <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 0,
  "message": null,
  "data": [
    {
      "id": 1,
      "sortId": 1,
      "labelName": "Java",
      "labelDescription": "Java编程语言"
    },
    // ...更多标签
  ]
}
                    </pre>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </el-form-item>
            
            <el-form-item v-if="apiConfig.enabled">
              <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
                
              </div>
            </el-form-item>
          </el-form>
        </div>
        
        <div style="margin-top: 20px; margin-bottom: 22px; text-align: center;">
          <el-button type="primary" @click="saveApiConfig" :loading="apiLoading">保存API配置</el-button>
          <el-button @click="resetApiConfig" style="margin-left: 10px;">重置修改</el-button>
        </div>
      </el-card>
    </div>

    <div>
      <el-button type="danger" @click="resetForm('ruleForm')">重置所有修改</el-button>
    </div>

    <!-- 高级配置对话框 -->
    <el-dialog title="邮箱高级配置" :visible.sync="advancedConfigVisible" width="600px">
      <el-form :model="currentAdvancedConfig" label-width="160px">
        <el-tabs v-model="activeConfigTab">
          <el-tab-pane label="基础设置" name="basic">
            <el-form-item label="连接超时(ms)">
              <el-input-number v-model="currentAdvancedConfig.connectionTimeout" :min="1000" :max="120000" :step="1000" :controls="false"></el-input-number>
            </el-form-item>
            <el-form-item label="超时时间(ms)">
              <el-input-number v-model="currentAdvancedConfig.timeout" :min="1000" :max="120000" :step="1000" :controls="false"></el-input-number>
            </el-form-item>
            <el-form-item label="JNDI名称">
              <el-input v-model="currentAdvancedConfig.jndiName" placeholder="可选"></el-input>
            </el-form-item>
            <el-form-item label="信任所有证书">
              <el-switch v-model="currentAdvancedConfig.trustAllCerts"></el-switch>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="协议设置" name="protocol">
            <el-form-item label="协议类型">
              <el-select v-model="currentAdvancedConfig.protocol" placeholder="请选择">
                <el-option label="SMTP" value="smtp"></el-option>
                <el-option label="SMTPS" value="smtps"></el-option>
                <el-option label="IMAP" value="imap"></el-option>
                <el-option label="IMAPS" value="imaps"></el-option>
                <el-option label="POP3" value="pop3"></el-option>
                <el-option label="POP3S" value="pop3s"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="认证机制">
              <el-select v-model="currentAdvancedConfig.authMechanism" placeholder="请选择">
                <el-option label="默认" value="default"></el-option>
                <el-option label="LOGIN" value="login"></el-option>
                <el-option label="PLAIN" value="plain"></el-option>
                <el-option label="CRAM-MD5" value="cram-md5"></el-option>
                <el-option label="DIGEST-MD5" value="digest-md5"></el-option>
                <el-option label="XOAUTH2" value="xoauth2"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="调试模式">
              <el-switch v-model="currentAdvancedConfig.debug"></el-switch>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="代理设置" name="proxy">
            <el-form-item label="使用代理">
              <el-switch v-model="currentAdvancedConfig.useProxy"></el-switch>
            </el-form-item>
            <el-form-item label="代理主机" v-if="currentAdvancedConfig.useProxy">
              <el-input v-model="currentAdvancedConfig.proxyHost" placeholder="代理服务器地址"></el-input>
            </el-form-item>
            <el-form-item label="代理端口" v-if="currentAdvancedConfig.useProxy">
              <el-input-number v-model="currentAdvancedConfig.proxyPort" :min="1" :max="65535" :controls="false"></el-input-number>
            </el-form-item>
            <el-form-item label="代理用户名" v-if="currentAdvancedConfig.useProxy">
              <el-input v-model="currentAdvancedConfig.proxyUser" placeholder="可选"></el-input>
            </el-form-item>
            <el-form-item label="代理密码" v-if="currentAdvancedConfig.useProxy">
              <el-input v-model="currentAdvancedConfig.proxyPassword" type="password" placeholder="可选"></el-input>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="自定义属性" name="custom">
            <el-alert
              title="高级用户选项"
              type="warning"
              description="这些属性直接传递给JavaMail，请确保您了解其含义"
              show-icon
              :closable="false"
              style="margin-bottom: 15px;">
            </el-alert>
            
            <div v-for="(value, key, index) in currentAdvancedConfig.customProperties" :key="index" style="display: flex; margin-bottom: 10px;">
              <el-input v-model="customPropertyKeys[index]" placeholder="属性名" style="width: 40%; margin-right: 10px;" @change="updateCustomPropertyKey(index)"></el-input>
              <el-input v-model="currentAdvancedConfig.customProperties[customPropertyKeys[index]]" placeholder="属性值" style="width: 50%"></el-input>
              <el-button type="danger" icon="el-icon-delete" circle style="margin-left: 10px;" @click="removeCustomProperty(index)"></el-button>
            </div>
            
            <el-button type="primary" icon="el-icon-plus" size="small" @click="addCustomProperty">添加自定义属性</el-button>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="advancedConfigVisible = false">取 消</el-button>
        <el-button type="primary" @click="saveAdvancedConfig">确 定</el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
  const uploadPicture = () => import( "../common/uploadPicture");
  const ApiTestTool = () => import( "./ApiTestTool");

  export default {
    components: {
      uploadPicture,
      ApiTestTool
    },
    data() {
      return {
        disabled: true,
        types: ['', 'success', 'info', 'danger', 'warning'],
        inputNoticeVisible: false,
        inputNoticeValue: "",
        inputRandomNameVisible: false,
        inputRandomNameValue: "",
        inputRandomAvatarVisible: false,
        inputRandomAvatarValue: "",
        inputRandomCoverVisible: false,
        inputRandomCoverValue: "",
        webInfo: {
          id: null,
          webName: "",
          webTitle: "",
          footer: "",
          backgroundImage: "/assets/backgroundPicture.jpg",
          avatar: "",
          waifuJson: "",
          enableWaifu: false,
          status: false,
          navConfig: "",
          footerBackgroundImage: "",
          footerBackgroundConfig: "",
          email: ""
        },
        notices: [],
        randomAvatar: [],
        randomName: [],
        randomCover: [],
        emailConfigs: [],
        emailTestDialogVisible: false,
        emailTestForm: {
          testEmail: '',
          currentConfig: null
        },
        defaultEmailIndex: -1,
        advancedConfigVisible: false,
        activeConfigTab: 'basic',
        currentAdvancedConfig: {
          connectionTimeout: 25000,
          timeout: 25000,
          jndiName: '',
          trustAllCerts: false,
          protocol: 'smtp',
          authMechanism: 'default',
          debug: false,
          useProxy: false,
          proxyHost: '',
          proxyPort: 8080,
          proxyUser: '',
          proxyPassword: '',
          customProperties: {}
        },
        customPropertyKeys: [],
        currentConfigIndex: -1,
        thirdLoginConfig: {
          enable: true,
          github: {
            client_id: '',
            client_secret: '',
            redirect_uri: 'http://localhost:5000/callback/github',
            enabled: true
          },
          google: {
            client_id: '',
            client_secret: '',
            redirect_uri: 'http://localhost:5000/callback/google',
            enabled: true
          },
          twitter: {
            client_key: '',
            client_secret: '',
            redirect_uri: 'http://localhost:5000/callback/x',
            enabled: true
          },
          yandex: {
            client_id: '',
            client_secret: '',
            redirect_uri: 'http://localhost:5000/callback/yandex',
            enabled: true
          },
          gitee: {
            client_id: '',
            client_secret: '',
            redirect_uri: 'http://localhost:5000/callback/gitee',
            enabled: true
          }
        },
        rules: {
          webName: [
            {required: true, message: '请输入网站名称', trigger: 'blur'},
            {min: 1, max: 10, message: '长度在 1 到 10 个字符', trigger: 'change'}
          ],
          webTitle: [
            {required: true, message: '请输入网站标题', trigger: 'blur'}
          ],
          footer: [
            // 移除长度限制，页脚内容完全自由
          ],
          backgroundImage: [
            {required: true, message: '请输入背景', trigger: 'change'}
          ],
          status: [
            {required: true, message: '请设置网站状态', trigger: 'change'}
          ],
          avatar: [
            {required: true, message: '请上传头像', trigger: 'change'}
          ]
        },
        testEmailLoading: false,
        isMobileDevice: false,
        loading: false,
        defaultMailIndex: -1,
        
        // 滑动验证码配置
        captchaConfig: {
          enable: false,
          login: true,
          register: true,
          comment: false,
          reset_password: true,
          screenSizeThreshold: 768,
          forceSlideForMobile: false,
          checkbox: {
            trackSensitivity: 0.9,
            minTrackPoints: 5
          },
          slide: {
            accuracy: 5,
            successThreshold: 0.9
          }
        },
        captchaLoading: false,
        activeCaptchaTab: 'checkbox',
        apiConfig: {
          enabled: false,
          apiKey: ''
        },
        apiLoading: false,
        apiTestTab: 'create',
        apiTestLoading: false,
        apiTestResult: null,
        apiTestForm: {
          title: '测试文章标题',
          content: '# 这是一个测试文章\n\n这是通过API测试工具创建的文章，支持**Markdown**格式。',
          sortName: '测试分类',
          labelName: '测试标签',
          cover: '',
          summary: '测试文章摘要'
        },
        apiQueryForm: {
          current: 1,
          size: 10,
          searchKey: ''
        },
        apiDetailForm: {
          id: null
        },
        navConfigText: "首页,记录,家,百宝箱,留言,联系我",
        navLoading: false,
        defaultNavItems: [
          { name: "首页", icon: "🏡", link: "/", type: "internal" },
          { name: "记录", icon: "📒", link: "#", type: "dropdown" },
          { name: "家", icon: "❤️‍🔥", link: "/love", type: "internal" },
          { name: "百宝箱", icon: "🧰", link: "/favorite", type: "internal" },
          { name: "留言", icon: "📪", link: "/message", type: "internal" },
          { name: "联系我", icon: "💬", link: "#chat", type: "special" }
        ],
        footerBgConfig: {
          backgroundSize: 'cover',
          backgroundPosition: 'center center',
          backgroundRepeat: 'no-repeat',
          opacity: 100,
          textShadow: false,
          maskColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    },

    computed: {
      thirdLoginTableData() {
        return [
          {
            platform: 'GitHub',
            type: 'github',
            config: this.thirdLoginConfig.github,
            enabled: this.thirdLoginConfig.github.enabled,
            developerUrl: 'https://github.com/settings/developers'
          },
          {
            platform: 'Google',
            type: 'google',
            config: this.thirdLoginConfig.google,
            enabled: this.thirdLoginConfig.google.enabled,
            developerUrl: 'https://console.cloud.google.com/apis/credentials'
          },
          {
            platform: 'Twitter',
            type: 'twitter',
            config: this.thirdLoginConfig.twitter,
            enabled: this.thirdLoginConfig.twitter.enabled,
            developerUrl: 'https://developer.twitter.com/en/portal/dashboard'
          },
          {
            platform: 'Yandex',
            type: 'yandex',
            config: this.thirdLoginConfig.yandex,
            enabled: this.thirdLoginConfig.yandex.enabled,
            developerUrl: 'https://oauth.yandex.com/'
          },
          {
            platform: 'Gitee',
            type: 'gitee',
            config: this.thirdLoginConfig.gitee,
            enabled: this.thirdLoginConfig.gitee.enabled,
            developerUrl: 'https://gitee.com/settings/applications'
          }
        ];
      },
      parsedNavItems() {
        // 处理空字符串情况
        if (!this.navConfigText.trim()) {
          return [];
        }
        
        // 分割文本并移除前后空格
        const navNames = this.navConfigText.split(',').map(name => name.trim());
        
        // 根据名称查找匹配的导航项
        return navNames.map(name => {
          const foundItem = this.defaultNavItems.find(item => item.name === name);
          return foundItem || { name: name, icon: "🔗", link: "/", type: "internal" };
        });
      }
    },

    created() {
      // 并行执行所有初始化请求
      this.initializeData();
    },

    mounted() {
      if (this.isMobile()) {
        this.isMobileDevice = true;
      }
      
      // 检测设备类型
      this.checkDeviceType();
      window.addEventListener('resize', this.checkDeviceType);
    },

    beforeDestroy() {
      // 移除监听器
      window.removeEventListener('resize', this.checkDeviceType);
    },

    methods: {
      // 新增：并行初始化所有数据
      async initializeData() {
        console.log("开始并行加载所有配置数据...");
        const startTime = Date.now();
        
        try {
          // 并行执行所有请求
          const promises = [
            this.getWebInfo(),
            this.getThirdLoginConfig(),
            this.getCaptchaConfig(),
            this.getEmailConfigs(),
            this.getApiConfig()
          ];
          
          // 等待所有请求完成
          await Promise.allSettled(promises);
          
          const endTime = Date.now();
          console.log(`所有配置数据加载完成，耗时: ${endTime - startTime}ms`);
        } catch (error) {
          console.error("初始化数据时出错:", error);
        }
      },
      addBackgroundImage(res) {
        this.webInfo.backgroundImage = res;
      },
      addAvatar(res) {
        this.webInfo.avatar = res;
      },
      addRandomAvatar(res) {
        this.randomAvatar.push(res);
      },
      addRandomCover(res) {
        this.randomCover.push(res);
      },
      changeWebStatus() {
        this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", {
          id: this.webInfo.id,
          status: this.webInfo.status
        }, true)
          .then((res) => {
            this.getWebInfo();
            this.$message({
              message: "保存成功！",
              type: "success"
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      // 优化：将getWebInfo改为异步方法
      async getWebInfo() {
        try {
          const res = await this.$http.get(this.$constant.pythonBaseURL + "/admin/webInfo/getAdminWebInfoDetails", {}, true);
            if (!this.$common.isEmpty(res.data)) {
              this.webInfo.id = res.data.id;
              this.webInfo.webName = res.data.webName;
              this.webInfo.webTitle = res.data.webTitle;
              this.webInfo.footer = res.data.footer;
              this.webInfo.backgroundImage = res.data.backgroundImage;
              this.webInfo.avatar = res.data.avatar;
              this.webInfo.waifuJson = res.data.waifuJson;
              this.webInfo.enableWaifu = res.data.enableWaifu;
              this.webInfo.status = res.data.status;
              this.webInfo.navConfig = res.data.navConfig || "[]";
              this.webInfo.footerBackgroundImage = res.data.footerBackgroundImage || "";
              this.webInfo.footerBackgroundConfig = res.data.footerBackgroundConfig || "";
              this.webInfo.email = res.data.email || "";
              this.notices = JSON.parse(res.data.notices);
              this.randomAvatar = JSON.parse(res.data.randomAvatar);
              this.randomName = JSON.parse(res.data.randomName);
              this.randomCover = JSON.parse(res.data.randomCover);
              
              // 加载页脚背景配置
              if (this.webInfo.footerBackgroundConfig) {
                try {
                  this.footerBgConfig = JSON.parse(this.webInfo.footerBackgroundConfig);
                } catch (e) {
                  console.error("解析页脚背景配置失败:", e);
                  // 使用默认配置
                  this.footerBgConfig = {
                    backgroundSize: 'cover',
                    backgroundPosition: 'center center',
                    backgroundRepeat: 'no-repeat',
                    opacity: 100,
                    textShadow: false,
                    maskColor: 'rgba(0, 0, 0, 0.5)'
                  };
                }
              }
              
              // 解析导航栏配置并转换为文本
              try {
                const navItems = JSON.parse(this.webInfo.navConfig || "[]");
                if (navItems.length > 0) {
                  // 提取出导航项名称并转为文本
                  this.navConfigText = navItems.map(item => item.name).join(',');
                } else {
                  this.resetToDefaultNav();
                  console.log("导航栏配置为空数组，使用默认配置");
                }
              } catch (e) {
                console.error("解析导航栏配置失败:", e);
                this.resetToDefaultNav();
              }
            }
        } catch (error) {
            this.$message({
              message: error.message,
              type: "error"
            });
          throw error; // 重新抛出错误，让Promise.allSettled能够捕获
        }
      },
      submitForm(formName) {
        this.$refs[formName].validate((valid) => {
          if (valid) {
            // 构建导航项数组
            const navItems = this.parsedNavItems.map((item, index) => ({
              ...item,
              order: index + 1,
              enabled: true
            }));
            
            // 更新WebInfo对象中的navConfig
            const webInfoToUpdate = {...this.webInfo};
            webInfoToUpdate.navConfig = JSON.stringify(navItems);
            
            // 更新页脚背景配置
            webInfoToUpdate.footerBackgroundConfig = JSON.stringify(this.footerBgConfig);
            
            this.updateWebInfo(webInfoToUpdate);
          } else {
            this.$message({
              message: "请完善必填项！",
              type: "error"
            });
          }
        });
      },
      resetForm(formName) {
        this.$refs[formName].resetFields();
        this.getWebInfo();
        this.resetToDefaultNav();
      },
      handleClose(array, item) {
        array.splice(array.indexOf(item), 1);
      },
      handleInputNoticeConfirm() {
        if (this.inputNoticeValue) {
          this.notices.push(this.inputNoticeValue);
        }
        this.inputNoticeVisible = false;
        this.inputNoticeValue = '';
      },
      showNoticeInput() {
        this.inputNoticeVisible = true;
        this.$nextTick(() => {
          this.$refs.saveNoticeInput.$refs.input.focus();
        });
      },
      saveNotice() {
        let param = {
          id: this.webInfo.id,
          notices: JSON.stringify(this.notices)
        }
        this.updateWebInfo(param);
      },
      handleInputRandomNameConfirm() {
        if (this.inputRandomNameValue) {
          this.randomName.push(this.inputRandomNameValue);
        }
        this.inputRandomNameVisible = false;
        this.inputRandomNameValue = '';
      },
      showRandomNameInput() {
        this.inputRandomNameVisible = true;
        this.$nextTick(() => {
          this.$refs.saveRandomNameInput.$refs.input.focus();
        });
      },
      saveRandomName() {
        let param = {
          id: this.webInfo.id,
          randomName: JSON.stringify(this.randomName)
        }
        this.updateWebInfo(param);
      },
      handleInputRandomAvatarConfirm() {
        if (this.inputRandomAvatarValue) {
          this.randomAvatar.push(this.inputRandomAvatarValue);
        }
        this.inputRandomAvatarVisible = false;
        this.inputRandomAvatarValue = '';
      },
      showRandomAvatarInput() {
        this.inputRandomAvatarVisible = true;
        this.$nextTick(() => {
          this.$refs.saveRandomAvatarInput.$refs.input.focus();
        });
      },
      saveRandomAvatar() {
        let param = {
          id: this.webInfo.id,
          randomAvatar: JSON.stringify(this.randomAvatar)
        }
        this.updateWebInfo(param);
      },
      handleInputRandomCoverConfirm() {
        if (this.inputRandomCoverValue) {
          this.randomCover.push(this.inputRandomCoverValue);
        }
        this.inputRandomCoverVisible = false;
        this.inputRandomCoverValue = '';
      },
      showRandomCoverInput() {
        this.inputRandomCoverVisible = true;
        this.$nextTick(() => {
          this.$refs.saveRandomCoverInput.$refs.input.focus();
        });
      },
      saveRandomCover() {
        let param = {
          id: this.webInfo.id,
          randomCover: JSON.stringify(this.randomCover)
        }
        this.updateWebInfo(param);
      },
      updateWebInfo(value) {
        this.$confirm('确认保存？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 创建一个副本，去除看板娘字段
          const javaValue = {...value};
          if('enableWaifu' in javaValue) {
            delete javaValue.enableWaifu;
          }
          
          // 创建看板娘请求数据
          const waifuData = {
            id: value.id,
            enableWaifu: value.enableWaifu
          };
          
          // 同时发送两个请求
          const promises = [
            // 发送基本信息到Java API
            this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", javaValue, true),
            // 发送看板娘状态到Python API
            this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateWaifuStatus", waifuData, true)
          ];
          
          // 处理所有请求完成
          Promise.all(promises)
            .then(([javaRes, pythonRes]) => {
              this.getWebInfo();
              this.$message({
                message: "保存成功！",
                type: "success"
              });
              
              // 如果请求中包含了enableWaifu字段，说明看板娘状态改变了，提示用户刷新
              if('enableWaifu' in value) {
                // 询问用户是否要刷新页面
                this.$confirm(
                  value.enableWaifu ? 
                    '看板娘已启用，需要刷新页面才能生效。现在刷新页面吗？' : 
                    '看板娘已禁用，需要刷新页面才能完全生效。现在刷新页面吗？', 
                  '刷新提示', 
                  {
                    confirmButtonText: '立即刷新',
                    cancelButtonText: '稍后刷新',
                    type: 'info'
                  }
                ).then(() => {
                  window.location.reload();
                }).catch(() => {
                  // 用户选择稍后刷新
                  this.$notify({
                    title: '提示',
                    message: '请注意，看板娘变更需要刷新页面后才能完全生效。',
                    type: 'warning',
                    duration: 5000
                  });
                });
              }
            })
            .catch((error) => {
              this.$message({
                message: error.message || "部分保存失败，请检查",
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消保存!'
          });
        });
      },
      // 优化：将getEmailConfigs改为异步方法
      async getEmailConfigs() {
        try {
          const res = await this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getEmailConfigs", {}, true);
            this.emailConfigs = res.data || [];
            // 修正属性名
            this.emailConfigs.forEach(config => {
              // 将ssl改为useSsl
              if (config.hasOwnProperty('ssl') && !config.hasOwnProperty('useSsl')) {
                config.useSsl = config.ssl;
                delete config.ssl;
              }
              // 将starttls改为useStarttls
              if (config.hasOwnProperty('starttls') && !config.hasOwnProperty('useStarttls')) {
                config.useStarttls = config.starttls;
                delete config.starttls;
              }
            });
            
            // 获取默认邮箱索引
          await this.getDefaultMailConfigIndex();
        } catch (error) {
            this.$message.error("获取邮箱配置失败: " + error.message);
          throw error;
        }
      },
      // 优化：将getDefaultMailConfigIndex改为异步方法
      async getDefaultMailConfigIndex() {
        try {
          const res = await this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getDefaultMailConfig", {}, true);
            this.defaultMailIndex = res.data || -1;
        } catch (error) {
            this.$message.error("获取默认邮箱索引失败: " + error.message);
          throw error;
        }
      },
      addEmailConfig() {
        // 默认配置
        const defaultConfig = {
          host: "",
          username: "",
          password: "",
          port: 465, // 默认SSL端口
          senderName: "Poetize",
          useSsl: true,
          useStarttls: false,
          auth: true,
          enabled: true,
          // 高级配置默认值
          connectionTimeout: 25000,
          timeout: 25000,
          protocol: 'smtp',
          authMechanism: 'default',
          debug: false,
          useProxy: false
        };
        
        // 弹出对话框，让用户选择预设配置
        this.$confirm('是否使用邮箱服务预设？', '添加邮箱', {
          confirmButtonText: '使用预设',
          cancelButtonText: '使用默认配置',
          type: 'info'
        }).then(() => {
          // 用户选择使用预设
          this.showPresetDialog();
        }).catch(() => {
          // 用户选择使用默认配置
          this.emailConfigs.push(defaultConfig);
        });
      },
      
      // 显示预设配置对话框
      showPresetDialog() {
        this.$alert(
          '<div style="text-align:center"><strong>请选择邮箱服务商</strong></div>' +
          '<div style="margin-top:15px; display:flex; flex-wrap:wrap; justify-content:space-around;">' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'163\')">网易163邮箱</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'qq\')">QQ邮箱</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'gmail\')">Gmail</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'outlook\')">Outlook</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'aliyun\')">阿里云邮箱</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'qqex\')">腾讯企业邮箱</button>' +
          '<button class="preset-btn" style="margin:5px; padding:10px; background:#fff; border:1px solid #dcdfe6; border-radius:4px; cursor:pointer;" @click="usePreset(\'163ex\')">网易企业邮箱</button>' +
          '</div>',
          '选择预设配置',
          {
            dangerouslyUseHTMLString: true,
            showConfirmButton: false,
            showCancelButton: true,
            cancelButtonText: '取消',
            closeOnClickModal: false,
            customClass: 'preset-dialog'
          }
        );
        
        // 将button的点击事件处理绑定到Vue实例上
        setTimeout(() => {
          const btns = document.querySelectorAll('.preset-dialog .preset-btn');
          btns.forEach(btn => {
            btn.addEventListener('click', () => {
              // 关闭对话框
              document.querySelector('.el-message-box__close').click();
              // 获取preset参数并调用usePreset方法
              const preset = btn.innerText.includes('163') ? '163' :
                             btn.innerText.includes('QQ') ? 'qq' :
                             btn.innerText.includes('Gmail') ? 'gmail' :
                             btn.innerText.includes('Outlook') ? 'outlook' : 'aliyun';
              this.usePreset(preset);
            });
          });
        }, 100);
      },
      
      // 使用预设配置
      usePreset(preset) {
        let config = {
          senderName: "Poetize",
          username: "",
          password: "",
          enabled: true,
          auth: true,
          // 高级配置默认值
          connectionTimeout: 25000,
          timeout: 25000,
          protocol: 'smtp',
          authMechanism: 'default',
          debug: false,
          useProxy: false
        };
        
        switch(preset) {
          case '163':
            config = {
              ...config,
              host: "smtp.163.com",
              port: 465,
              useSsl: true,
              useStarttls: false
            };
            break;
          case 'qq':
            config = {
              ...config,
              host: "smtp.qq.com",
              port: 465,
              useSsl: true,
              useStarttls: false
            };
            break;
          case 'gmail':
            config = {
              ...config,
              host: "smtp.gmail.com",
              port: 587,
              useSsl: false,
              useStarttls: true
            };
            break;
          case 'outlook':
            config = {
              ...config,
              host: "smtp.office365.com",
              port: 587,
              useSsl: false,
              useStarttls: true
            };
            break;
          case 'aliyun':
            config = {
              ...config,
              host: "smtp.aliyun.com",
              port: 465,
              useSsl: true,
              useStarttls: false
            };
            break;
          case 'qqex':
            config = {
              ...config,
              host: "smtp.exmail.qq.com",
              port: 465,
              useSsl: true,
              useStarttls: false
            };
            break;
          case '163ex':
            config = {
              ...config,
              host: "smtp.qiye.163.com",
              port: 465,
              useSsl: true,
              useStarttls: false
            };
            break;
        }
        
        this.emailConfigs.push(config);
        this.$message({
          message: `已添加${preset}邮箱预设配置，请填写您的邮箱地址和授权码`,
          type: "success"
        });
      },
      removeEmailConfig(index) {
        this.emailConfigs.splice(index, 1);
      },
      saveEmailConfigs() {
        // 验证配置
        const validConfigs = this.emailConfigs.filter(config => {
          return config.host && config.username && config.password;
        });
        
        if (validConfigs.length === 0) {
          this.$message.error("请至少配置一个完整的邮箱");
            return;
        }
        
        this.loading = true;
        
        // 保存配置
        this.$http.post(this.$constant.pythonBaseURL + "/webInfo/saveEmailConfigs?defaultIndex=" + this.defaultMailIndex, 
          validConfigs, true)
          .then((res) => {
            this.$message.success("邮箱配置保存成功");
            this.loading = false;
          })
          .catch((error) => {
            this.$message.error("保存失败: " + error.message);
            this.loading = false;
          });
      },
      testEmailConfig(config, index) {
        this.emailTestForm.testEmail = "";
        this.emailTestForm.configIndex = index;
        this.emailTestDialogVisible = true;
      },
      submitTestEmail() {
        if (!this.emailTestForm.testEmail) {
          this.$message.error("请输入测试邮箱地址");
          return;
        }
        
        this.testEmailLoading = true;
        
        // 构建测试数据
        const testConfig = this.emailConfigs[this.emailTestForm.configIndex];
        const testData = {
          host: testConfig.host,
          port: testConfig.port,
          username: testConfig.username,
          password: testConfig.password,
          useSsl: testConfig.useSsl,
          useStarttls: testConfig.useStarttls,
          auth: testConfig.auth,
          senderName: testConfig.senderName,
          testTo: this.emailTestForm.testEmail
        };
        
        // 发送测试请求
        this.$http.post(this.$constant.pythonBaseURL + "/webInfo/testEmailConfig", testData, true)
          .then(res => {
            this.$message.success("测试邮件发送成功，请查收");
            this.emailTestDialogVisible = false;
            this.testEmailLoading = false;
          })
          .catch(error => {
            this.$message.error("测试邮件发送失败: " + error.message);
            this.testEmailLoading = false;
          });
      },
      showAdvancedConfig(config, index) {
        this.currentConfigIndex = index;
        // 初始化高级配置
        this.currentAdvancedConfig = {
          connectionTimeout: config.connectionTimeout || 25000,
          timeout: config.timeout || 25000,
          jndiName: config.jndiName || '',
          trustAllCerts: config.trustAllCerts || false
        };
        this.advancedConfigVisible = true;
      },
      saveAdvancedConfig() {
        // 确保索引有效
        if (this.currentConfigIndex >= 0 && this.currentConfigIndex < this.emailConfigs.length) {
          // 更新高级配置
          const config = this.emailConfigs[this.currentConfigIndex];
          
          // 基础配置
          config.connectionTimeout = this.currentAdvancedConfig.connectionTimeout;
          config.timeout = this.currentAdvancedConfig.timeout;
          config.jndiName = this.currentAdvancedConfig.jndiName;
          config.trustAllCerts = this.currentAdvancedConfig.trustAllCerts;
          
          // 协议配置
          config.protocol = this.currentAdvancedConfig.protocol;
          config.authMechanism = this.currentAdvancedConfig.authMechanism;
          config.debug = this.currentAdvancedConfig.debug;
          
          // 代理配置
          config.useProxy = this.currentAdvancedConfig.useProxy;
          if (config.useProxy) {
            config.proxyHost = this.currentAdvancedConfig.proxyHost;
            config.proxyPort = this.currentAdvancedConfig.proxyPort;
            config.proxyUser = this.currentAdvancedConfig.proxyUser;
            config.proxyPassword = this.currentAdvancedConfig.proxyPassword;
          }
          
          // 自定义属性
          config.customProperties = {...this.currentAdvancedConfig.customProperties};
          
          this.$message({
            message: "高级配置已保存",
            type: "success"
          });
        }
        this.advancedConfigVisible = false;
      },
      updateEnableWaifu() {
        // 获取实际状态（通过请求确认当前状态）
        this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getWaifuStatus", true)
          .then((res) => {
            if (res.code === 200 && res.data) {
              // 使用后端返回的实际状态
              const actualStatus = res.data.enableWaifu;
              this.webInfo.enableWaifu = actualStatus; // 同步前端状态
              
              // 计算目标状态
              const newStatus = !actualStatus;
              const action = newStatus ? "启用" : "禁用";
              
              // 询问用户
              this.$confirm(`确认${action}看板娘功能吗？`, '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'info'
              }).then(() => {
                // 用户确认，更新状态
                this.webInfo.enableWaifu = newStatus;
                console.log(`切换看板娘状态为: ${newStatus}`);
                
                // 准备要更新的数据
                const param = {
                  id: this.webInfo.id,
                  enableWaifu: newStatus
                };
                
                // 调用Python后端API更新状态
                this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateWaifuStatus", param, true, {
                  headers: {
                    'Authorization': localStorage.getItem('adminToken')
                  }
                })
                .then((res) => {
                  if (res.code === 200) {
                    // 更新本地存储 - 使用与前端一致的格式
                    const webInfoStr = localStorage.getItem('webInfo');
                    let webInfoData = {};
                    
                    try {
                      // 尝试解析现有数据
                      if (webInfoStr) {
                        webInfoData = JSON.parse(webInfoStr);
                      }
                      
                      // 更新数据
                      if (webInfoData.data) {
                        // 新格式
                        webInfoData.data.enableWaifu = newStatus;
                        webInfoData.timestamp = Date.now();
                      } else {
                        // 旧格式或新存储
                        webInfoData = {
                          timestamp: Date.now(),
                          data: {
                            ...this.webInfo,
                            enableWaifu: newStatus
                          }
                        };
                      }
                      
                      // 保存回localStorage
                      localStorage.setItem('webInfo', JSON.stringify(webInfoData));
                      console.log('更新本地存储的看板娘状态:', newStatus);
                      
                    } catch (e) {
                      console.error('更新本地存储失败:', e);
                      // 使用简单格式保存
                      localStorage.setItem('webInfo', JSON.stringify({
                        timestamp: Date.now(),
                        data: {
                          id: this.webInfo.id,
                          enableWaifu: newStatus
                        }
                      }));
                    }
                    
                    this.$message({
                      message: newStatus ? 
                        "看板娘已启用，是否刷新页面立即生效？" : 
                        "看板娘已禁用，是否刷新页面立即生效？",
                      type: "success",
                      duration: 5000
                    });
                    
                    // 询问用户是否要刷新页面
                    this.$confirm('是否立即刷新页面使更改生效？', '提示', {
                      confirmButtonText: '立即刷新',
                      cancelButtonText: '稍后刷新',
                      type: 'success'
                    }).then(() => {
                      window.location.reload();
                    }).catch(() => {
                      this.$message({
                        type: 'info',
                        message: '您可以稍后手动刷新页面使更改生效'
                      });
                    });
                  } else {
                    // 恢复状态
                    this.webInfo.enableWaifu = actualStatus;
                    this.$message({
                      message: "更新看板娘状态失败: " + res.message,
                      type: "error",
                      duration: 3000
                    });
                  }
                })
                .catch((error) => {
                  // 恢复状态
                  this.webInfo.enableWaifu = actualStatus;
                  this.$message({
                    message: "更新看板娘状态失败: " + error.message,
                    type: "error",
                    duration: 3000
                  });
                });
              }).catch(() => {
                // 用户取消，恢复状态
                this.webInfo.enableWaifu = actualStatus;
                this.$message({
                  type: 'info',
                  message: '已取消操作'
                });
              });
            } else {
              this.$message({
                message: "获取看板娘状态失败",
                type: "error",
                duration: 3000
              });
            }
          })
          .catch((error) => {
            this.$message({
              message: "获取看板娘状态失败: " + error.message,
              type: "error",
              duration: 3000
            });
          });
      },
      // 优化：将getThirdLoginConfig改为异步方法
      async getThirdLoginConfig() {
        console.log("开始获取第三方登录配置...");
        try {
          const res = await this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getThirdLoginConfig", true);
          console.log("第三方登录配置API响应:", res);
          console.log("响应数据:", res.data);
          
          // 确保返回完整的配置结构
          const defaultConfig = {
              enable: false,
            github: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/github',
              enabled: false
            },
            google: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/google',
              enabled: false
            },
            twitter: {
              client_key: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/x',
              enabled: false
            },
            yandex: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/yandex',
              enabled: false
            },
            gitee: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/gitee',
              enabled: false
            }
          };
          
          if (res.data) {
            console.log("开始合并配置数据...");
            // 合并返回的数据和默认配置，确保所有必需的属性都存在
            this.thirdLoginConfig = {
              enable: res.data.enable !== undefined ? res.data.enable : defaultConfig.enable,
              github: {
                ...defaultConfig.github,
                ...(res.data.github || {})
              },
              google: {
                ...defaultConfig.google,
                ...(res.data.google || {})
              },
              twitter: {
                ...defaultConfig.twitter,
                ...(res.data.twitter || {})
              },
              yandex: {
                ...defaultConfig.yandex,
                ...(res.data.yandex || {})
              },
              gitee: {
                ...defaultConfig.gitee,
                ...(res.data.gitee || {})
              }
            };
            console.log("合并后的第三方登录配置:", this.thirdLoginConfig);
          } else {
            console.log("API返回数据为空，使用默认配置");
            this.thirdLoginConfig = defaultConfig;
            console.log("默认第三方登录配置:", this.thirdLoginConfig);
          }
        } catch (error) {
          console.error("获取第三方登录配置失败:", error);
          console.log("错误详情:", error.response || error.message);
          // 设置完整的默认配置
          this.thirdLoginConfig = {
            enable: false,
            github: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/github',
              enabled: false
            },
            google: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/google',
              enabled: false
            },
            twitter: {
              client_key: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/x',
              enabled: false
            },
            yandex: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/yandex',
              enabled: false
            },
            gitee: {
              client_id: '',
              client_secret: '',
              redirect_uri: 'http://localhost:5000/callback/gitee',
              enabled: false
            }
          };
          console.log("使用错误处理的默认配置:", this.thirdLoginConfig);
            this.$message.error("获取第三方登录配置失败: " + error.message);
          throw error;
        }
      },
      
      // 优化：将getApiConfig改为异步方法
      async getApiConfig() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + "/webInfo/getApiConfig", true);
            if (res.data) {
              this.apiConfig = res.data;
            } else {
              this.apiConfig = {
                enabled: false,
                apiKey: ''
              };
            }
        } catch (error) {
            this.$message.error("获取API配置失败: " + error.message);
          throw error;
        }
      },
      saveThirdLoginConfig() {
        // 检查配置是否有效
        let hasInvalidConfig = false;
        
        // 检查GitHub配置
        if (this.thirdLoginConfig.github.enabled) {
          if (!this.thirdLoginConfig.github.client_id) {
            this.$message.error("GitHub的Client ID不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.github.client_secret) {
            this.$message.error("GitHub的Client Secret不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.github.redirect_uri) {
            this.$message.error("GitHub的回调地址不能为空");
            hasInvalidConfig = true;
          }
        }
        
        // 检查Google配置
        if (this.thirdLoginConfig.google.enabled) {
          if (!this.thirdLoginConfig.google.client_id) {
            this.$message.error("Google的Client ID不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.google.client_secret) {
            this.$message.error("Google的Client Secret不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.google.redirect_uri) {
            this.$message.error("Google的回调地址不能为空");
            hasInvalidConfig = true;
          }
        }
        
        // 检查Twitter配置
        if (this.thirdLoginConfig.twitter.enabled) {
          if (!this.thirdLoginConfig.twitter.client_key) {
            this.$message.error("Twitter的API Key不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.twitter.client_secret) {
            this.$message.error("Twitter的API Secret不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.twitter.redirect_uri) {
            this.$message.error("Twitter的回调地址不能为空");
            hasInvalidConfig = true;
          }
        }
        
        // 检查Yandex配置
        if (this.thirdLoginConfig.yandex.enabled) {
          if (!this.thirdLoginConfig.yandex.client_id) {
            this.$message.error("Yandex的Client ID不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.yandex.client_secret) {
            this.$message.error("Yandex的Client Secret不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.yandex.redirect_uri) {
            this.$message.error("Yandex的回调地址不能为空");
            hasInvalidConfig = true;
          }
        }
        
        // 检查Gitee配置
        if (this.thirdLoginConfig.gitee.enabled) {
          if (!this.thirdLoginConfig.gitee.client_id) {
            this.$message.error("Gitee的Client ID不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.gitee.client_secret) {
            this.$message.error("Gitee的Client Secret不能为空");
            hasInvalidConfig = true;
          }
          if (!this.thirdLoginConfig.gitee.redirect_uri) {
            this.$message.error("Gitee的回调地址不能为空");
            hasInvalidConfig = true;
          }
        }
        
        if (hasInvalidConfig) {
          return;
        }
        
        this.loading = true;
        this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateThirdLoginConfig", this.thirdLoginConfig, true)
          .then((res) => {
            this.$message({
              message: "第三方登录配置保存成功",
              type: "success"
            });
            this.loading = false;
          })
          .catch((error) => {
            this.$message({
              message: error.message || "保存失败",
              type: "error"
            });
            this.loading = false;
          });
      },
      updateThirdLoginConfig() {
        this.loading = true;
        this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateThirdLoginConfig", this.thirdLoginConfig, true)
          .then((res) => {
            this.$message({
              message: "第三方登录功能" + (this.thirdLoginConfig.enable ? "已启用" : "已禁用"),
              type: "success"
            });
            this.loading = false;
          })
          .catch((error) => {
            this.$message({
              message: error.message || "操作失败",
              type: "error"
            });
            // 出错时恢复原状态
            this.thirdLoginConfig.enable = !this.thirdLoginConfig.enable;
            this.loading = false;
          });
      },
      testThirdLogin(platform) {
        if (!platform.config.client_id && (platform.type !== 'twitter' || !platform.config.client_key)) {
          this.$message({
            message: "请先填写Client ID/API Key",
            type: "warning"
          });
          return;
        }

        if (!platform.config.client_secret) {
          this.$message({
            message: "请先填写Client Secret",
            type: "warning"
          });
          return;
        }

        this.$message({
          message: `正在测试${platform.platform}登录...`,
          type: "info"
        });

        // 打开登录窗口进行测试
        window.open(`/login/${platform.type}`, '_blank');
      },
      getPlatformIcon(type) {
        const iconMapping = {
          github: './static/svg/github.svg',
          google: './static/svg/google.svg',
          twitter: './static/svg/x.svg',
          yandex: './static/svg/yandex.svg',
          gitee: './static/svg/gitee.png'
        };
        return iconMapping[type] || '';
      },
      // 测试第三方登录
      testLogin(type) {
        // 检查配置是否完整
        const platformConfig = this.thirdLoginConfig[type];
        if (!platformConfig) {
          this.$message.error("无法找到该平台配置");
          return;
        }
        
        // 检查是否启用
        if (!this.thirdLoginConfig.enable || !platformConfig.enabled) {
          this.$message.error("该平台登录功能未启用");
          return;
        }
        
        // 检查配置是否完整
        if (type === 'twitter' && (!platformConfig.client_key || !platformConfig.client_secret)) {
          this.$message.error("请先填写完整的API Key和Secret");
          return;
        } else if (type !== 'twitter' && (!platformConfig.client_id || !platformConfig.client_secret)) {
          this.$message.error("请先填写完整的Client ID和Secret");
          return;
        }
        
        // 打开登录测试窗口
        const loginUrl = `${this.$constant.pythonBaseURL}/login/${type === 'twitter' ? 'x' : type}`;
        window.open(loginUrl, '_blank', 'width=800,height=600');
      },
      // 检测设备类型
      checkDeviceType() {
        this.isMobileDevice = window.innerWidth <= 768 || this.isMobile();
      },
      // 处理默认邮箱变更
      handleDefaultChange(index) {
        this.defaultMailIndex = index;
      },
      // 删除邮箱配置
      deleteEmailConfig(index) {
        this.$confirm('确定要删除这个邮箱配置吗?', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.emailConfigs.splice(index, 1);
          if (this.defaultMailIndex === index) {
            this.defaultMailIndex = -1;
          } else if (this.defaultMailIndex > index) {
            this.defaultMailIndex--;
          }
          this.$message({
            type: 'success',
            message: '删除成功!'
          });
        }).catch(() => {});
      },
      addNewEmailConfig() {
        // 创建新的邮箱配置对象
        const newEmailConfig = {
          host: '',
          username: '',
          password: '',
          port: '465',
          ssl: true,
          starttls: false,
          auth: true,
          enabled: true,
          // 添加高级配置
          connectionTimeout: 25000,
          timeout: 25000,
          jndiName: '',
          trustAllCerts: false,
          protocol: 'smtp',
          authMechanism: 'default',
          debug: false,
          useProxy: false,
          proxyHost: '',
          proxyPort: 8080,
          proxyUser: '',
          proxyPassword: '',
          customProperties: {}
        };
        
        // 添加到配置列表
        this.emailConfigs.push(newEmailConfig);
      },
      
      // 显示高级配置对话框
      showAdvancedConfig(index) {
        this.currentConfigIndex = index;
        // 获取当前配置的高级设置
        const config = this.emailConfigs[index];
        this.currentAdvancedConfig = {
          connectionTimeout: config.connectionTimeout || 25000,
          timeout: config.timeout || 25000,
          jndiName: config.jndiName || '',
          trustAllCerts: config.trustAllCerts || false,
          protocol: config.protocol || 'smtp',
          authMechanism: config.authMechanism || 'default',
          debug: config.debug || false,
          useProxy: config.useProxy || false,
          proxyHost: config.proxyHost || '',
          proxyPort: config.proxyPort || 8080,
          proxyUser: config.proxyUser || '',
          proxyPassword: config.proxyPassword || '',
          customProperties: config.customProperties ? {...config.customProperties} : {}
        };
        
        // 设置自定义属性键名数组
        this.customPropertyKeys = Object.keys(this.currentAdvancedConfig.customProperties || {});
        
        this.activeConfigTab = 'basic';
        this.advancedConfigVisible = true;
      },
      
      // 添加自定义属性
      addCustomProperty() {
        const newKey = `mail.property.${this.customPropertyKeys.length + 1}`;
        this.$set(this.currentAdvancedConfig.customProperties, newKey, '');
        this.customPropertyKeys.push(newKey);
      },
      
      // 更新自定义属性的键
      updateCustomPropertyKey(index) {
        const oldKey = Object.keys(this.currentAdvancedConfig.customProperties)[index];
        const newKey = this.customPropertyKeys[index];
        
        if (oldKey !== newKey && newKey) {
          const value = this.currentAdvancedConfig.customProperties[oldKey];
          this.$delete(this.currentAdvancedConfig.customProperties, oldKey);
          this.$set(this.currentAdvancedConfig.customProperties, newKey, value);
        }
      },
      
      // 删除自定义属性
      removeCustomProperty(index) {
        const key = this.customPropertyKeys[index];
        this.$delete(this.currentAdvancedConfig.customProperties, key);
        this.customPropertyKeys.splice(index, 1);
      },
      
      // 保存高级配置
      saveAdvancedConfig() {
        // 确保索引有效
        if (this.currentConfigIndex >= 0 && this.currentConfigIndex < this.emailConfigs.length) {
          // 更新当前配置的高级设置
          const config = this.emailConfigs[this.currentConfigIndex];
          
          // 基础配置
          config.connectionTimeout = this.currentAdvancedConfig.connectionTimeout;
          config.timeout = this.currentAdvancedConfig.timeout;
          config.jndiName = this.currentAdvancedConfig.jndiName;
          config.trustAllCerts = this.currentAdvancedConfig.trustAllCerts;
          
          // 协议配置
          config.protocol = this.currentAdvancedConfig.protocol;
          config.authMechanism = this.currentAdvancedConfig.authMechanism;
          config.debug = this.currentAdvancedConfig.debug;
          
          // 代理配置
          config.useProxy = this.currentAdvancedConfig.useProxy;
          if (config.useProxy) {
            config.proxyHost = this.currentAdvancedConfig.proxyHost;
            config.proxyPort = this.currentAdvancedConfig.proxyPort;
            config.proxyUser = this.currentAdvancedConfig.proxyUser;
            config.proxyPassword = this.currentAdvancedConfig.proxyPassword;
          }
          
          // 自定义属性
          config.customProperties = {...this.currentAdvancedConfig.customProperties};
          
          this.$message({
            message: "高级配置已保存",
            type: "success"
          });
        }
        this.advancedConfigVisible = false;
      },
      // 处理端口变化，自动调整SSL/STARTTLS设置
      onPortChange(port, config, index) {
        if (port === 465) {
          // 端口465必须使用SSL
          if (!config.useSsl) {
            config.useSsl = true;
            this.$message({
              message: "端口465必须使用SSL加密，已自动启用SSL",
              type: "info"
            });
          }
          // 通常465端口不使用STARTTLS
          if (config.useStarttls) {
            config.useStarttls = false;
          }
        } else if (port === 587) {
          // 端口587通常使用STARTTLS
          if (!config.useStarttls) {
            config.useStarttls = true;
            this.$message({
              message: "端口587通常使用STARTTLS加密，已自动启用STARTTLS",
              type: "info"
            });
          }
          // 587端口通常不使用SSL
          if (config.useSsl) {
            config.useSsl = false;
          }
        } else if (port === 25) {
          // 端口25通常不加密，显示警告
          this.$message({
            message: "警告：端口25通常不加密，且可能被ISP阻止。建议使用465(SSL)或587(STARTTLS)端口",
            type: "warning"
          });
        }
      },
      // 处理SSL开关变化
      onSslChange(value, config, index) {
        if (!value && config.port === 465) {
          // 如果用户尝试在端口465上禁用SSL，自动重新启用并提示
          this.$nextTick(() => {
            config.useSsl = true;
            this.$message({
              message: "端口465必须使用SSL加密！",
              type: "warning"
            });
          });
        }
      },
      
      // 处理STARTTLS开关变化
      onStarttlsChange(value, config, index) {
        if (!value && config.port === 587) {
          // 如果用户尝试在端口587上禁用STARTTLS，给出警告提示
          this.$message({
            message: "警告：端口587通常需要启用STARTTLS，禁用可能导致邮件发送失败",
            type: "warning"
          });
        }
      },
      
      // 优化：将getCaptchaConfig改为异步方法
      async getCaptchaConfig() {
        try {
          const res = await this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getCaptchaConfig", {}, true);
            if (res.data) {
              this.captchaConfig = res.data;
            }
        } catch (error) {
            this.$message.error("获取智能验证码配置失败: " + error.message);
          throw error;
        }
      },
      
      // 更新智能验证码全局状态
      updateCaptchaStatus(value) {
        // 先设置本地状态，确保UI立即响应
        this.captchaConfig.enable = value;
        
        if (!value) {
          // 如果禁用了智能验证码，显示确认对话框
          this.$confirm('禁用智能验证码将降低网站安全性，可能导致机器人攻击，确定要禁用吗?', '安全提示', {
            confirmButtonText: '确定禁用',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(() => {
            // 用户确认禁用，保持禁用状态并保存
            this.saveCaptchaConfig();
          }).catch(() => {
            // 用户取消，恢复开关状态为启用
            this.captchaConfig.enable = true;
          });
        } else {
          // 启用验证码，直接保存
          this.saveCaptchaConfig();
        }
      },
      
      // 保存智能验证码配置
      saveCaptchaConfig() {
        this.captchaLoading = true;
        this.$http.post(this.$constant.pythonBaseURL + "/webInfo/updateCaptchaConfig", this.captchaConfig, true)
          .then((res) => {
            this.$message.success("智能验证码配置保存成功");
            this.captchaLoading = false;
          })
          .catch((error) => {
            this.$message.error("保存智能验证码配置失败: " + error.message);
            this.captchaLoading = false;
          });
      },
      regenerateApiKey() {
        this.apiLoading = true;
        this.$http.post(this.$constant.baseURL + "/webInfo/regenerateApiKey", {}, true)
          .then((res) => {
            this.apiConfig.apiKey = res.data;
            this.$message({
              message: "API密钥已重新生成",
              type: "success"
            });
            this.apiLoading = false;
          })
          .catch((error) => {
            this.$message({
              message: "重新生成API密钥失败: " + error.message,
              type: "error"
            });
            this.apiLoading = false;
          });
      },
      saveApiConfig() {
        this.apiLoading = true;
        this.$http.post(this.$constant.baseURL + "/webInfo/saveApiConfig", this.apiConfig, true)
          .then((res) => {
            this.$message({
              message: "API配置保存成功",
              type: "success"
            });
            this.apiLoading = false;
          })
          .catch((error) => {
            this.$message({
              message: "保存API配置失败: " + error.message,
              type: "error"
            });
            this.apiLoading = false;
          });
      },
      isMobile() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
      },
      testCreateArticle() {
        if (!this.apiTestForm.title || !this.apiTestForm.content) {
          this.$message.warning('文章标题和内容不能为空');
          return;
        }
        
        this.apiTestLoading = true;
        
        const data = {
          title: this.apiTestForm.title,
          content: this.apiTestForm.content,
          sortName: this.apiTestForm.sortName,
          labelName: this.apiTestForm.labelName,
          cover: this.apiTestForm.cover || undefined,
          summary: this.apiTestForm.summary || undefined,
          viewStatus: true,
          commentStatus: true,
          submitToSearchEngine: false
        };
        
        this.$http.post(this.$constant.baseURL + "/api/article/create", data, {
          headers: {
            'X-API-KEY': this.apiConfig.apiKey
          }
        })
          .then(res => {
            this.apiTestResult = JSON.stringify(res, null, 2);
            this.apiTestLoading = false;
          })
          .catch(error => {
            this.apiTestResult = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
            this.apiTestLoading = false;
          });
      },
      
      testQueryArticles() {
        this.apiTestLoading = true;
        
        const params = {
          current: this.apiQueryForm.current,
          size: this.apiQueryForm.size
        };
        
        if (this.apiQueryForm.searchKey) {
          params.searchKey = this.apiQueryForm.searchKey;
        }
        
        this.$http.get(this.$constant.baseURL + "/api/article/list", {
          params: params,
          headers: {
            'X-API-KEY': this.apiConfig.apiKey
          }
        })
          .then(res => {
            this.apiTestResult = JSON.stringify(res, null, 2);
            this.apiTestLoading = false;
          })
          .catch(error => {
            this.apiTestResult = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
            this.apiTestLoading = false;
          });
      },
      
      testGetArticleDetail() {
        if (!this.apiDetailForm.id) {
          this.$message.warning('文章ID不能为空');
          return;
        }
        
        this.apiTestLoading = true;
        
        this.$http.get(this.$constant.baseURL + "/api/article/" + this.apiDetailForm.id, {
          headers: {
            'X-API-KEY': this.apiConfig.apiKey
          }
        })
          .then(res => {
            this.apiTestResult = JSON.stringify(res, null, 2);
            this.apiTestLoading = false;
          })
          .catch(error => {
            this.apiTestResult = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
            this.apiTestLoading = false;
          });
      },
      
      testGetCategories() {
        this.apiTestLoading = true;
        
        this.$http.get(this.$constant.baseURL + "/api/categories", {
          headers: {
            'X-API-KEY': this.apiConfig.apiKey
          }
        })
          .then(res => {
            this.apiTestResult = JSON.stringify(res, null, 2);
            this.apiTestLoading = false;
          })
          .catch(error => {
            this.apiTestResult = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
            this.apiTestLoading = false;
          });
      },
      
      testGetTags() {
        this.apiTestLoading = true;
        
        this.$http.get(this.$constant.baseURL + "/api/tags", {
          headers: {
            'X-API-KEY': this.apiConfig.apiKey
          }
        })
          .then(res => {
            this.apiTestResult = JSON.stringify(res, null, 2);
            this.apiTestLoading = false;
          })
          .catch(error => {
            this.apiTestResult = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
            this.apiTestLoading = false;
          });
      },
      resetApiConfig() {
        this.getApiConfig();
      },
      resetToDefaultNav() {
        this.navConfigText = "首页,记录,家,百宝箱,留言,联系我";
      },
      saveNavConfig() {
        this.navLoading = true;
        
        // 构建导航项数组
        const navItems = this.parsedNavItems.map((item, index) => ({
          ...item,
          order: index + 1,
          enabled: true
        }));
        
        // 更新WebInfo对象中的navConfig
        const param = {
          id: this.webInfo.id,
          navConfig: JSON.stringify(navItems)
        };
        
        this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", param, true)
          .then((res) => {
            this.$message({
              message: "导航栏配置保存成功",
              type: "success"
            });
            this.navLoading = false;
          })
          .catch((error) => {
            this.$message({
              message: "保存导航栏配置失败: " + error.message,
              type: "error"
            });
            this.navLoading = false;
          });
      },
      // 打开开发者中心页面
      openDeveloperCenter(url) {
        if (url) {
          window.open(url, '_blank');
        } else {
          this.$message.warning('开发者中心链接未配置');
        }
      },
      handleWaifuChange(value) {
        this.webInfo.enableWaifu = value;
      },
      handleThirdLoginToggle(value) {
        this.thirdLoginConfig.enable = value;
      },
      handlePlatformToggle(platform, value) {
        this.thirdLoginConfig[platform].enabled = value;
      },
      handleCaptchaToggle(value) {
        // 先设置本地状态，确保UI立即响应
        this.captchaConfig.enable = value;
        
        if (!value) {
          // 如果禁用了智能验证码，显示确认对话框
          this.$confirm('禁用智能验证码将降低网站安全性，可能导致机器人攻击，确定要禁用吗?', '安全提示', {
            confirmButtonText: '确定禁用',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(() => {
            // 用户确认禁用，保持禁用状态并保存
            this.saveCaptchaConfig();
          }).catch(() => {
            // 用户取消，恢复开关状态为启用
            this.captchaConfig.enable = true;
          });
        } else {
          // 启用验证码，直接保存
          this.saveCaptchaConfig();
        }
      },
      handleApiToggle(value) {
        this.apiConfig.enabled = value;
      },
      addFooterBackgroundImage(res) {
        this.webInfo.footerBackgroundImage = res;
      }
    }
  }
</script>

<style scoped>

  .my-tag {
    margin-bottom: 20px !important;
    width: 100%;
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .el-tag {
    margin: 10px;
  }

  .button-new-tag {
    margin: 10px;
    height: 32px;
    line-height: 32px;
    padding-top: 0;
    padding-bottom: 0;
  }

  .input-new-tag {
    width: 200px;
    margin: 10px;
  }

  .my-icon {
    cursor: pointer;
    margin-left: 10px;
    font-size: 18px;
    font-weight: bold;
    color: var(--blue);
  }

  .table-td-thumb {
    border-radius: 2px;
    width: 40px;
    height: 40px;
  }

  .template-hint {
    color: #909399;
    font-size: 12px;
    margin-top: 5px;
  }

  .third-login-config .platform-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 20px;
    margin-bottom: 20px;
  }

  .platform-card {
    border-radius: 8px;
    transition: all 0.3s;
  }

  .platform-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);
  }

  .platform-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
    padding-bottom: 15px;
    border-bottom: 1px solid #f0f0f0;
  }

  .platform-logo {
    display: flex;
    align-items: center;
  }

  .platform-name {
    font-size: 18px;
    font-weight: 500;
    margin-left: 10px;
  }

  .platform-form {
    margin-bottom: 15px;
  }

  .platform-actions {
    display: flex;
    justify-content: flex-end;
    padding-top: 10px;
    border-top: 1px dashed #f0f0f0;
  }

  /* 平台特定样式 */
  .github-card .platform-header {
    color: #333;
  }

  .google-card .platform-header {
    color: #4285F4;
  }

  .twitter-card .platform-header {
    color: #1DA1F2;
  }

  .yandex-card .platform-header {
    color: #FF0000;
  }

  /* 响应式表格样式 */
  .responsive-table-container {
    width: 100%;
    overflow-x: auto;
  }

  /* 移动设备样式 */
  @media screen and (max-width: 768px) {
    .el-table__header-wrapper {
      font-size: 13px;
    }
    
    .el-table--small td {
      padding: 8px 0;
    }
    
    .mobile-table .el-table__row {
      cursor: pointer;
    }
    
    .table-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-start;
    }
    
    .table-actions .el-button {
      margin: 2px 5px;
      padding: 5px;
    }
    
    .delete-btn {
      color: #f56c6c;
    }
  }

  .captcha-card-disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .tag-icon {
    margin-top: 20px;
  }

  .icon {
    width: 16px;
    height: 16px;
  }

  /* 导航栏预览样式 */
  .nav-preview-section {
    margin-top: 15px;
    border-top: 1px solid #EBEEF5;
    padding-top: 15px;
  }

  .nav-preview-title {
    font-weight: bold;
    margin-bottom: 10px;
  }

  .nav-preview-container {
    background-color: rgba(0,0,0,0.7);
    padding: 8px 15px;
    border-radius: 4px;
    display: flex;
    flex-wrap: wrap;
    min-height: 40px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    margin-bottom: 5px;
    align-items: center;
  }

  .nav-item-preview {
    margin-right: 15px;
    color: white;
    padding: 5px 0;
    display: flex;
    align-items: center;
    cursor: pointer;
    position: relative;
    transition: all 0.3s;
  }

  .nav-item-preview:hover {
    color: var(--themeBackground, #409EFF);
    transform: translateY(-2px);
  }

  .nav-item-preview:hover::after {
    content: "";
    position: absolute;
    bottom: -2px;
    left: 0;
    width: 100%;
    height: 2px;
    background-color: var(--themeBackground, #409EFF);
    animation: navItemHover 0.3s ease-in-out;
  }

  @keyframes navItemHover {
    from {
      width: 0;
    }
    to {
      width: 100%;
    }
  }

  .nav-item-icon {
    margin-right: 5px;
    font-size: 16px;
  }

  .nav-item-name {
    font-size: 14px;
  }

  .nav-item-dropdown {
    font-size: 12px;
    margin-left: 3px;
  }

</style>
