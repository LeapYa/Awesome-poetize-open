<template>
  <div class="translation-management">
      
    <!-- 主要配置区域 -->
    <div class="config-container">
      <el-form :model="apiConfig" label-width="120px" class="config-form">

        <!-- 全局AI模型配置 -->
        <div class="config-section">
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
              全局AI模型配置
            </el-tag>
          </div>
          <div class="section-content">
            <el-alert
              title="此AI模型配置将用于智能摘要、AI翻译等所有AI功能"
              type="info"
              :closable="false"
              show-icon
              style="margin:10px; margin-bottom: 20px;">
            </el-alert>
            
            <el-form-item id="field-translation-global-llm-type" label="大模型类型">
              <el-select v-model="apiConfig.llmType" @change="onLlmTypeChange" placeholder="请选择大模型类型" class="full-width">
                <el-option label="OpenAI / ChatGPT API" value="openai">
              <span class="option-content">
                    OpenAI / ChatGPT API
              </span>
            </el-option>
                <el-option label="Anthropic (Claude)" value="anthropic">
              <span class="option-content">
                    Anthropic (Claude)
                  </span>
                </el-option>
                <el-option label="硅基流动" value="siliconflow">
                  <span class="option-content">
                    硅基流动
                  </span>
                </el-option>
                <el-option label="DeepSeek" value="deepseek">
                  <span class="option-content">
                    DeepSeek
                  </span>
                </el-option>
                <el-option label="OpenRouter" value="openrouter">
                  <span class="option-content">
                    OpenRouter
                  </span>
                </el-option>
                <el-option label="WorldRouter" value="worldrouter">
                  <span class="option-content">
                    WorldRouter
                  </span>
                </el-option>
                <el-option label="Azure OpenAI" value="azure">
                  <span class="option-content">
                    Azure OpenAI
                  </span>
                </el-option>
                <el-option label="自定义/其他" value="custom">
                  <span class="option-content">
                    自定义/其他
              </span>
            </el-option>
          </el-select>
        </el-form-item>
            
            <el-form-item id="field-translation-global-llm-model" label="模型名称">
              <el-input 
                v-model="apiConfig.llmModel" 
                :placeholder="getModelPlaceholder()" 
                class="input-field">
              </el-input>
              <div :class="{'form-tip': true, 'custom-model-tip': apiConfig.llmType === 'custom'}">
                <i class="el-icon-lightbulb"></i>
                {{ getModelTip() }}
          </div>
            </el-form-item>
            
            <!-- 自定义模型的接口类型选择 -->
            <el-form-item label="接口类型" v-if="apiConfig.llmType === 'custom'">
              <el-select v-model="apiConfig.llmInterfaceType" placeholder="请选择接口类型" class="full-width">
                <el-option label="自动检测" value="auto">
                  <span class="option-content">
                    自动检测
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口(/v1/chat/completions)" value="openai">
                  <span class="option-content">
                    OpenAI兼容接口(/v1/chat/completions)
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口(/v1/completions)" value="openai_completions" disabled>
                  <span class="option-content">
                    OpenAI兼容接口(/v1/completions)
                  </span>
                </el-option>
                <el-option label="Anthropic兼容接口" value="anthropic">
                  <span class="option-content">
                    Anthropic兼容接口
                  </span>
                </el-option>
                <el-option label="自定义OpenAI兼容接口" value="custom">
                  <span class="option-content">
                    自定义OpenAI兼容接口
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip"><i class="el-icon-info"></i>{{ getLlmInterfaceTip(apiConfig.llmInterfaceType) }}</div>
            </el-form-item>
            
            <el-form-item id="field-translation-global-llm-url" label="API接口地址">
              <el-input v-model="apiConfig.llmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>
            </el-form-item>

            <el-form-item label="接口平台">
              <el-select v-model="apiConfig.llmThinkingProfile" placeholder="自动识别" class="full-width">
                <el-option label="自动识别" value="auto"></el-option>
                <el-option label="OpenRouter" value="openrouter"></el-option>
                <el-option label="WorldRouter" value="worldrouter"></el-option>
                <el-option label="硅基流动" value="siliconflow"></el-option>
                <el-option label="DeepSeek 官方" value="deepseek_official"></el-option>
                <el-option label="OpenAI" value="openai"></el-option>
                <el-option label="Anthropic" value="anthropic"></el-option>
                <el-option label="通用 OpenAI 兼容" value="generic_openai_compatible"></el-option>
              </el-select>
              <div class="form-tip"><i class="el-icon-info"></i>按服务商和 API 地址自动识别；不准时可手动选择接口平台。</div>
            </el-form-item>
            <el-form-item label="自定义请求参数">
              <el-input v-model="apiConfig.llmThinkingExtraBodyText" type="textarea" :rows="3" placeholder='例如：{"include_reasoning":true}' class="textarea-field"></el-input>
              <div class="form-tip"><i class="el-icon-info"></i>JSON 对象。系统生成的平台参数会覆盖同名字段。根据实际需要填写，可不填。</div>
            </el-form-item>
            
            <el-form-item id="field-translation-global-llm-key" label="API密钥" v-if="needsApiKey || apiConfig.hasExistingLlmKey || apiConfig.clearExistingLlmKey">
              <el-input v-model="apiConfig.llmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field" @input="cancelSecretClear('llm')">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.clearExistingLlmKey">
                  保存后将清除已保存密钥。对于本地模型（如Ollama）可以不填写
                </template>
                <template v-else-if="apiConfig.hasExistingLlmKey">
                  已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥。对于本地模型（如Ollama）可以不填写
                </template>
                <template v-else>
                  API密钥将自动加密存储，确保您的数据安全。对于本地模型（如Ollama）可以不填写
                </template>
              </div>
              <div v-if="apiConfig.hasExistingLlmKey || apiConfig.clearExistingLlmKey" class="secret-actions">
                <el-button v-if="apiConfig.hasExistingLlmKey" type="text" size="mini" @click="markSecretForClear('llm')">
                  清除已保存密钥
                </el-button>
                <el-button v-if="apiConfig.clearExistingLlmKey" type="text" size="mini" @click="cancelSecretClear('llm', true)">
                  撤销清除
                </el-button>
              </div>
            </el-form-item>
            
            <el-form-item label="超时时间">
              <div class="timeout-group">
                <el-input v-model.number="apiConfig.llmTimeout" placeholder="请输入超时时间" class="timeout-input">
                  <template slot="append">秒</template>
                </el-input>
              </div>
            </el-form-item>

            <el-form-item label="Max Tokens">
              <el-input
                v-model="apiConfig.llmMaxTokens"
                inputmode="numeric"
                placeholder="最大生成令牌数"
                class="input-field"
                @input="sanitizeMaxTokensField('llmMaxTokens', $event)"
                @blur="normalizeMaxTokensField('llmMaxTokens')">
                <template slot="append">tokens</template>
              </el-input>
              <div class="form-tip"><i class="el-icon-info"></i>最大生成令牌数，默认1000（思考模型建议2000+）</div>
            </el-form-item>

            <el-form-item id="field-translation-global-llm-reasoning" label="思考程度">
              <el-select v-model="apiConfig.llmReasoningEffort" clearable placeholder="不传入" class="full-width">
                <el-option label="低" value="low"></el-option>
                <el-option label="中" value="medium"></el-option>
                <el-option label="高" value="high"></el-option>
                <el-option label="超高" value="xhigh"></el-option>
              </el-select>
              <div class="form-tip"><i class="el-icon-info"></i>仅对支持 reasoning_effort / thinking 的模型生效，如 GPT 推理模型、DeepSeek V4 Pro。</div>
            </el-form-item>

            <el-form-item label="Temperature（可选）">
              <el-input-number v-model="apiConfig.llmTemperature" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
              <div class="form-tip"><i class="el-icon-info"></i>控制输出随机性（0-2），默认0.7</div>
            </el-form-item>

            <el-form-item label="Top P（可选）">
              <el-input-number v-model="apiConfig.llmTopP" :min="0" :max="1" :step="0.01" :precision="2" class="input-field"></el-input-number>
              <div class="form-tip"><i class="el-icon-info"></i>核采样参数（0-1），默认1.0</div>
            </el-form-item>

            <el-form-item label="频率惩罚（可选）">
              <el-input-number v-model="apiConfig.llmFrequencyPenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
              <div class="form-tip"><i class="el-icon-info"></i>降低重复词汇频率（0-2），默认0</div>
            </el-form-item>

            <el-form-item label="存在惩罚（可选）">
              <el-input-number v-model="apiConfig.llmPresencePenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
              <div class="form-tip"><i class="el-icon-info"></i>鼓励谈论新话题（0-2），默认0</div>
            </el-form-item>
            
            <!-- 测试全局AI连接按钮 -->
            <el-form-item label=" " style="margin-top: 20px;">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-button type="success" @click="testGlobalAi" class="action-btn success-btn" :loading="testGlobalAiLoading">
                  <i class="el-icon-link"></i>
                  测试连接
                </el-button>
                <span v-if="testGlobalAiError" style="color: #F56C6C; font-size: 14px;">
                  <i class="el-icon-warning"></i>
                  {{ testGlobalAiError }}
                </span>
              </div>
            </el-form-item>
          </div>
        </div>

        <!-- 翻译功能配置 -->
        <div class="config-section">
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
              翻译功能配置
          </el-tag>
        </div>
          <div class="section-content">
            <el-form-item id="field-translation-mode" label="翻译实现方式">
              <el-select v-model="apiConfig.mode" placeholder="请选择翻译实现方式" style="width: 240px" class="mrb10">
                <el-option key="none" label="不翻译" :value="'none'">
                  <span class="option-content">
                    <i class="el-icon-close"></i>
                    不翻译
                  </span>
                </el-option>
                <el-option key="api" label="API翻译" :value="'api'">
                  <span class="option-content">
                    <i class="el-icon-connection"></i>
                    API翻译
                  </span>
                </el-option>
                <el-option key="llm" label="使用全局AI模型" :value="'llm'">
                  <span class="option-content">
                    <i class="el-icon-chat-round"></i>
                    使用全局AI模型
                  </span>
                </el-option>
                <el-option key="dedicated_llm" label="使用独立AI模型" :value="'dedicated_llm'">
                  <span class="option-content">
                    <i class="el-icon-cpu"></i>
                    使用独立AI模型
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.mode === 'none'">
                  不使用翻译功能，文章将只保留源语言版本
                </template>
                <template v-else-if="apiConfig.mode === 'api'">
                  使用传统翻译API服务（国内云厂商、国际服务商或自定义HTTP接口），不走大模型
                </template>
                <template v-else-if="apiConfig.mode === 'llm'">
                  使用上方配置的全局AI模型进行翻译
                </template>
                <template v-else-if="apiConfig.mode === 'dedicated_llm'">
                  为翻译功能配置独立的AI模型，可以使用不同的模型和密钥
                </template>
              </div>
            </el-form-item>

            <!-- 语言配置 -->
            <div class="language-config-row">
              <el-form-item id="field-translation-source-lang" label="默认源语言" class="language-item">
                <el-select 
                  v-model="apiConfig.defaultSourceLang" 
                  placeholder="请选择默认源语言" 
                  class="language-select"
                  :disabled="hasArticles">
                  <template slot="prefix" v-if="hasArticles">
                    <el-tooltip content="⚠️ 系统中已有文章数据，源语言已锁定无法修改" placement="top" effect="dark">
                      <i class="el-icon-lock" style="color: #909399; margin-right: 5px;"></i>
                    </el-tooltip>
                  </template>
                  <el-option label="自动检测" value="auto">
                    <span class="option-content">
                      自动检测
                    </span>
                  </el-option>
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      中文
                    </span>
                  </el-option>
                  <el-option label="繁体中文" value="zh-TW">
                    <span class="option-content">
                      繁体中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      俄文
                    </span>
                  </el-option>
                </el-select>
                <small v-if="hasArticles" class="help-text" style="color: #E6A23C;">
                  <i class="el-icon-warning"></i> 源语言已锁定（系统中已有文章数据）
                </small>
              </el-form-item>
              
              <div class="language-arrow">
                <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
                  <path d="M38.29170707 485.95626872l621.31194136-1e-8L659.60364842 543.48515232 38.29170707 543.48515232l1e-8-57.5288836z" fill="currentColor"></path>
                  <path d="M656.10601111 313.80503752L990.65731211 510.28754791l-334.551301 201.79284823 0-398.27535862z" fill="currentColor"></path>
                </svg>
              </div>
              
              <el-form-item id="field-translation-target-lang" label="默认目标语言" class="language-item">
                <el-select v-model="apiConfig.defaultTargetLang" placeholder="请选择默认目标语言" class="language-select">
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      中文
                    </span>
                  </el-option>
                  <el-option label="繁体中文" value="zh-TW">
                    <span class="option-content">
                      繁体中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      俄文
                    </span>
                  </el-option>
                </el-select>

              </el-form-item>
        </div>

        <!-- API翻译配置 -->
            <template v-if="apiConfig.mode === 'api'">
          <el-form-item id="field-translation-api-provider" label="翻译引擎">
              <el-select v-model="apiConfig.provider" @change="onApiProviderChange" placeholder="请选择翻译引擎" class="full-width">
                <el-option-group
                  v-for="group in apiProviderGroups"
                  :key="group.label"
                  :label="group.label">
                  <el-option
                    v-for="provider in group.options"
                    :key="provider.value"
                    :label="provider.label"
                    :value="provider.value">
                    <span class="option-content">
                      <i :class="provider.icon"></i>
                      {{ provider.label }}
                    </span>
                  </el-option>
                </el-option-group>
            </el-select>
              <div class="form-tip"><i class="el-icon-info"></i>{{ getApiProviderDescription() }}</div>
          </el-form-item>

            <el-form-item v-if="isApiFieldVisible('customUrl')" :label="getApiFieldLabel('customUrl')">
              <el-input v-model="apiConfig.customUrl" :placeholder="getApiFieldPlaceholder('customUrl')" class="input-field"></el-input>
              <div class="form-tip" v-if="apiConfig.provider === 'custom'">
                <i class="el-icon-info"></i>这里填写原始HTTP翻译接口的完整URL，系统不会自动补全 /v1 或 /chat/completions。
              </div>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerAuthType')" :label="getApiFieldLabel('providerAuthType')">
              <el-select v-model="apiConfig.providerAuthType" class="full-width">
                <el-option label="Token" value="token"></el-option>
                <el-option label="AK/SK" value="aksk"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('endpointType')" :label="getApiFieldLabel('endpointType')">
              <el-select v-model="apiConfig.endpointType" class="full-width">
                <el-option label="Free API" value="free"></el-option>
                <el-option label="Pro API" value="pro"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('appId')" :label="getApiFieldLabel('appId')">
              <el-input v-model="apiConfig.appId" :placeholder="getApiFieldPlaceholder('appId')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('customApiKey')" :label="getApiFieldLabel('customApiKey')">
              <el-input v-model="apiConfig.customApiKey" type="password" show-password :placeholder="getApiFieldPlaceholder('customApiKey')" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="hasExistingApiFieldSecret('customApiKey')">
                  已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                </template>
                <template v-else>
                  API密钥将自动加密存储，确保您的数据安全
                </template>
              </div>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('appSecret')" :label="getApiFieldLabel('appSecret')">
              <el-input v-model="apiConfig.appSecret" type="password" show-password :placeholder="getApiFieldPlaceholder('appSecret')" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="hasExistingApiFieldSecret('appSecret')">
                  已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                </template>
                <template v-else>
                  API密钥将自动加密存储，确保您的数据安全
                </template>
              </div>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerSessionToken')" :label="getApiFieldLabel('providerSessionToken')">
              <el-input v-model="apiConfig.providerSessionToken" type="password" show-password :placeholder="getApiFieldPlaceholder('providerSessionToken')" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="hasExistingApiFieldSecret('providerSessionToken')">
                  已有 Session Token 已加密保存，留空则保持不变
                </template>
                <template v-else>
                  临时凭证可选，长期 AK/SK 可不填
                </template>
              </div>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerRegion')" :label="getApiFieldLabel('providerRegion')">
              <el-input v-model="apiConfig.providerRegion" :placeholder="getApiFieldPlaceholder('providerRegion')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerProjectId')" :label="getApiFieldLabel('providerProjectId')">
              <el-input v-model="apiConfig.providerProjectId" :placeholder="getApiFieldPlaceholder('providerProjectId')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerScene')" :label="getApiFieldLabel('providerScene')">
              <el-input v-model="apiConfig.providerScene" :placeholder="getApiFieldPlaceholder('providerScene')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerFormat')" :label="getApiFieldLabel('providerFormat')">
              <el-input v-model="apiConfig.providerFormat" :placeholder="getApiFieldPlaceholder('providerFormat')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerModel')" :label="getApiFieldLabel('providerModel')">
              <el-input v-model="apiConfig.providerModel" :placeholder="getApiFieldPlaceholder('providerModel')" class="input-field"></el-input>
            </el-form-item>

            <el-form-item v-if="isApiFieldVisible('providerCategory')" :label="getApiFieldLabel('providerCategory')">
              <el-input v-model="apiConfig.providerCategory" :placeholder="getApiFieldPlaceholder('providerCategory')" class="input-field"></el-input>
            </el-form-item>

              <div class="info-panel">
                <div class="info-header">
                  <i class="el-icon-question"></i>
                  {{ getApiProviderName() }} 使用说明
                </div>
                <div class="info-content">
                  <div class="info-item" v-for="item in getApiProviderHelp()" :key="item">• {{ item }}</div>
                </div>
              </div>
            </template>
            
            <!-- 使用全局AI模型时的配置 -->
            <template v-if="apiConfig.mode === 'llm'">
              <el-alert
                title="将使用上方配置的全局AI模型进行翻译"
                type="success"
                :closable="false"
                show-icon
                style="margin:10px; margin-bottom: 20px;">
              </el-alert>
              
              <el-form-item label="翻译提示词">
                <el-input type="textarea" v-model="apiConfig.llmPrompt" :rows="3" placeholder="请输入翻译提示词，用于指导大模型如何进行翻译" class="textarea-field"></el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  系统已支持输入输出TOON/JSON/CSV格式, 具体格式由提示词指定, 后端会自动解析TOON/JSON/CSV; 提示词中可使用占位符：{source_lang}源语言名称，{target_lang}目标语言名称，{toon_data}TOON格式文章数据（token更省），{json_data}JSON格式文章数据（简洁直观），{csv_data}CSV格式文章数据，{format}文本格式（单文本翻译使用）
                  <a href="javascript:void(0)" @click="showPromptDialog('toon')" style="margin-left: 6px;">TOON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('json')" style="margin-left: 6px;">JSON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('csv')" style="margin-left: 6px;">CSV格式提示词</a>
                </div>
              </el-form-item>
            </template>
            
            <!-- 使用独立AI模型时的配置 -->
            <template v-if="apiConfig.mode === 'dedicated_llm'">
              <el-alert
                title="为翻译功能配置独立的AI模型"
                type="info"
                :closable="false"
                show-icon
                style="margin:10px; margin-bottom: 20px;">
              </el-alert>
              
              <el-form-item label="大模型类型">
                <el-select v-model="apiConfig.translationLlmType" @change="onTranslationLlmTypeChange" placeholder="请选择大模型类型" class="full-width">
                  <el-option label="OpenAI / ChatGPT API" value="openai">
                    <span class="option-content">
                      OpenAI / ChatGPT API
                    </span>
                  </el-option>
                  <el-option label="Anthropic (Claude)" value="anthropic">
                    <span class="option-content">
                      Anthropic (Claude)
                    </span>
                  </el-option>
                  <el-option label="硅基流动" value="siliconflow">
                    <span class="option-content">
                      硅基流动
                    </span>
                  </el-option>
                  <el-option label="DeepSeek" value="deepseek">
                    <span class="option-content">
                      DeepSeek
                    </span>
                  </el-option>
                  <el-option label="OpenRouter" value="openrouter">
                    <span class="option-content">
                      OpenRouter
                    </span>
                  </el-option>
                  <el-option label="WorldRouter" value="worldrouter">
                    <span class="option-content">
                      WorldRouter
                    </span>
                  </el-option>
                  <el-option label="Azure OpenAI" value="azure">
                    <span class="option-content">
                      Azure OpenAI
                    </span>
                  </el-option>
                  <el-option label="自定义/其他" value="custom">
                    <span class="option-content">
                      自定义/其他
                    </span>
                  </el-option>
                </el-select>
              </el-form-item>
              
              <el-form-item label="模型名称">
                <el-input 
                  v-model="apiConfig.translationLlmModel" 
                  placeholder="请输入模型名称" 
                  class="input-field">
                </el-input>
              </el-form-item>
              
              <el-form-item label="接口类型" v-if="apiConfig.translationLlmType === 'custom'">
                <el-select v-model="apiConfig.translationLlmInterfaceType" placeholder="请选择接口类型" class="full-width">
                  <el-option label="自动检测" value="auto">
                    <span class="option-content">
                      自动检测
                    </span>
                  </el-option>
                  <el-option label="OpenAI兼容接口(/v1/chat/completions)" value="openai">
                    <span class="option-content">
                      OpenAI兼容接口(/v1/chat/completions)
                    </span>
                  </el-option>
                  <el-option label="OpenAI兼容接口(/v1/completions)" value="openai_completions" disabled>
                    <span class="option-content">
                      OpenAI兼容接口(/v1/completions)
                    </span>
                  </el-option>
                  <el-option label="Anthropic兼容接口" value="anthropic">
                    <span class="option-content">
                      Anthropic兼容接口
                    </span>
                  </el-option>
                  <el-option label="自定义OpenAI兼容接口" value="custom">
                    <span class="option-content">
                      自定义OpenAI兼容接口
                    </span>
                  </el-option>
                </el-select>
                <div class="form-tip"><i class="el-icon-info"></i>{{ getLlmInterfaceTip(apiConfig.translationLlmInterfaceType) }}</div>
              </el-form-item>
              
              <el-form-item label="API接口地址">
                <el-input v-model="apiConfig.translationLlmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>
              </el-form-item>

              <el-form-item label="接口平台">
                <el-select v-model="apiConfig.translationLlmThinkingProfile" placeholder="自动识别" class="full-width">
                  <el-option label="自动识别" value="auto"></el-option>
                  <el-option label="OpenRouter" value="openrouter"></el-option>
                  <el-option label="WorldRouter" value="worldrouter"></el-option>
                  <el-option label="硅基流动" value="siliconflow"></el-option>
                  <el-option label="DeepSeek 官方" value="deepseek_official"></el-option>
                  <el-option label="OpenAI" value="openai"></el-option>
                  <el-option label="Anthropic" value="anthropic"></el-option>
                  <el-option label="通用 OpenAI 兼容" value="generic_openai_compatible"></el-option>
                </el-select>
                <div class="form-tip"><i class="el-icon-info"></i>用于翻译独立模型的接口平台识别。</div>
              </el-form-item>
              <el-form-item label="自定义请求参数">
                <el-input v-model="apiConfig.translationLlmThinkingExtraBodyText" type="textarea" :rows="3" placeholder='例如：{"reasoning":{"enabled":true}}' class="textarea-field"></el-input>
                <div class="form-tip"><i class="el-icon-info"></i>JSON 对象。系统生成的平台参数会覆盖同名字段。根据实际需要填写，可不填。</div>
              </el-form-item>
              
              <el-form-item label="API密钥">
                <el-input v-model="apiConfig.translationLlmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field" @input="cancelSecretClear('translation')">
                  <template slot="prefix">
                    <i class="el-icon-lock"></i>
                  </template>
                </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.clearExistingTranslationLlmKey">
                    保存后将清除已保存密钥
                  </template>
                  <template v-else-if="apiConfig.hasExistingTranslationLlmKey">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                    API密钥将自动加密存储，确保您的数据安全
                  </template>
                </div>
                <div v-if="apiConfig.hasExistingTranslationLlmKey || apiConfig.clearExistingTranslationLlmKey" class="secret-actions">
                  <el-button v-if="apiConfig.hasExistingTranslationLlmKey" type="text" size="mini" @click="markSecretForClear('translation')">
                    清除已保存密钥
                  </el-button>
                  <el-button v-if="apiConfig.clearExistingTranslationLlmKey" type="text" size="mini" @click="cancelSecretClear('translation', true)">
                    撤销清除
                  </el-button>
                </div>
              </el-form-item>
              
              <el-form-item label="超时时间">
                <div class="timeout-group">
                  <el-input v-model.number="apiConfig.translationLlmTimeout" placeholder="请输入超时时间" class="timeout-input">
                    <template slot="append">秒</template>
                  </el-input>
                </div>
              </el-form-item>

              <el-form-item label="Max Tokens">
                <el-input
                  v-model="apiConfig.translationLlmMaxTokens"
                  inputmode="numeric"
                  placeholder="最大生成令牌数"
                  class="input-field"
                  @input="sanitizeMaxTokensField('translationLlmMaxTokens', $event)"
                  @blur="normalizeMaxTokensField('translationLlmMaxTokens')">
                  <template slot="append">tokens</template>
                </el-input>
                <div class="form-tip"><i class="el-icon-info"></i>最大生成令牌数，默认1000</div>
              </el-form-item>

              <el-form-item label="思考程度">
                <el-select v-model="apiConfig.translationLlmReasoningEffort" clearable placeholder="不传入" class="full-width">
                  <el-option label="低" value="low"></el-option>
                  <el-option label="中" value="medium"></el-option>
                  <el-option label="高" value="high"></el-option>
                  <el-option label="超高" value="xhigh"></el-option>
                </el-select>
                <div class="form-tip"><i class="el-icon-info"></i>仅在当前独立模型支持思考参数时传入。</div>
              </el-form-item>

              <el-form-item label="Temperature（可选）">
                <el-input-number v-model="apiConfig.translationLlmTemperature" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>控制输出随机性（0-2），默认0.7</div>
              </el-form-item>

              <el-form-item label="Top P（可选）">
                <el-input-number v-model="apiConfig.translationLlmTopP" :min="0" :max="1" :step="0.01" :precision="2" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>核采样参数（0-1），默认1.0</div>
              </el-form-item>

              <el-form-item label="频率惩罚（可选）">
                <el-input-number v-model="apiConfig.translationLlmFrequencyPenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>降低重复词汇频率（0-2），默认0</div>
              </el-form-item>

              <el-form-item label="存在惩罚（可选）">
                <el-input-number v-model="apiConfig.translationLlmPresencePenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>鼓励谈论新话题（0-2），默认0</div>
              </el-form-item>
              
              <el-form-item label="翻译提示词">
                <el-input type="textarea" v-model="apiConfig.llmPrompt" :rows="3" placeholder="请输入翻译提示词，用于指导大模型如何进行翻译" class="textarea-field"></el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  系统已支持输入输出TOON/JSON/CSV格式, 具体格式由提示词指定, 后端会自动解析TOON/JSON/CSV; 提示词中可使用占位符：{source_lang}源语言名称，{target_lang}目标语言名称，{toon_data}TOON格式文章数据（token更省），{json_data}JSON格式文章数据（简洁直观），{csv_data}CSV格式文章数据，{format}文本格式（单文本翻译使用）
                  <a href="javascript:void(0)" @click="showPromptDialog('toon')" style="margin-left: 6px;">TOON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('json')" style="margin-left: 6px;">JSON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('csv')" style="margin-left: 6px;">CSV格式提示词</a>
                </div>
              </el-form-item>
            </template>
            
            <!-- 测试翻译按钮 -->
            <el-form-item label=" " style="margin-top: 20px;">
              <el-button type="success" @click="testTranslation" class="action-btn success-btn">
                <i class="el-icon-link"></i>
                测试翻译
              </el-button>
            </el-form-item>
          </div>
        </div>
        
        <!-- 智能摘要功能配置 -->
        <div class="config-section">
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
              智能摘要功能配置
            </el-tag>
          </div>
          <div class="section-content">
            <el-form-item id="field-translation-summary-mode" label="摘要生成方式">
              <el-select v-model="apiConfig.summaryMode" placeholder="请选择摘要生成方式" style="width: 220px" class="mrb10">
                <el-option key="disabled" label="不自动生成摘要" :value="'disabled'">
                  <span class="option-content">
                    <i class="el-icon-remove-outline"></i>
                    不自动生成摘要
                  </span>
                </el-option>
                <el-option key="global" label="使用全局AI模型" :value="'global'">
                  <span class="option-content">
                    <i class="el-icon-s-grid"></i>
                    使用全局AI模型
                  </span>
                </el-option>
                <el-option key="dedicated" label="使用独立AI模型" :value="'dedicated'">
                  <span class="option-content">
                    <i class="el-icon-setting"></i>
                    使用独立AI模型
                  </span>
                </el-option>
                <el-option key="textrank" label="本地摘录（非AI）" :value="'textrank'">
                  <span class="option-content">
                    <i class="el-icon-data-analysis"></i>
                    本地摘录（非AI）
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.summaryMode === 'disabled'">
                  保存或更新文章时不自动生成摘要，未填写摘要时前端会使用文章开头作为展示兜底
                </template>
                <template v-else-if="apiConfig.summaryMode === 'global'">
                  将使用上方配置的全局AI模型生成摘要，效果好，需要API密钥
                </template>
                <template v-else-if="apiConfig.summaryMode === 'dedicated'">
                  为摘要功能配置独立的AI模型，可以使用不同的模型和密钥
                </template>
                <template v-else-if="apiConfig.summaryMode === 'textrank'">
                  本地算法只抽取/拼接原文片段，不理解文章含义，适合作为摘录兜底，不建议当作正式摘要
                </template>
              </div>
            </el-form-item>
            
            <template v-if="apiConfig.summaryMode === 'global' || apiConfig.summaryMode === 'dedicated'">
              
              <!-- 使用全局AI模型 -->
              <template v-if="apiConfig.summaryMode === 'global'">
                <el-alert
                  title="将使用上方配置的全局AI模型生成摘要"
                  type="success"
                  :closable="false"
                  show-icon
                  style="margin: 10px;margin-bottom: 20px;">
                </el-alert>
              </template>
              
              <!-- 使用独立AI模型 -->
              <template v-if="apiConfig.summaryMode === 'dedicated'">
                <el-alert
                  title="为摘要功能配置独立的AI模型"
                  type="info"
                  :closable="false"
                  show-icon
                  style="margin:10px; margin-bottom: 20px;">
                </el-alert>
                
          <el-form-item label="大模型类型">
                  <el-select v-model="apiConfig.summaryLlmType" @change="onSummaryLlmTypeChange" placeholder="请选择大模型类型" class="full-width">
                <el-option label="OpenAI / ChatGPT API" value="openai">
                  <span class="option-content">
                    OpenAI / ChatGPT API
                  </span>
                </el-option>
                <el-option label="Anthropic (Claude)" value="anthropic">
                  <span class="option-content">
                    Anthropic (Claude)
                  </span>
                </el-option>
                <el-option label="硅基流动" value="siliconflow">
                  <span class="option-content">
                    硅基流动
                  </span>
                </el-option>
                <el-option label="DeepSeek" value="deepseek">
                  <span class="option-content">
                    DeepSeek
                  </span>
                </el-option>
                <el-option label="OpenRouter" value="openrouter">
                  <span class="option-content">
                    OpenRouter
                  </span>
                </el-option>
                <el-option label="WorldRouter" value="worldrouter">
                  <span class="option-content">
                    WorldRouter
                  </span>
                </el-option>
                <el-option label="Azure OpenAI" value="azure">
                  <span class="option-content">
                    Azure OpenAI
                  </span>
                </el-option>
                <el-option label="自定义/其他" value="custom">
                  <span class="option-content">
                    自定义/其他
                  </span>
                </el-option>
            </el-select>
          </el-form-item>
            
          <el-form-item label="模型名称">
            <el-input 
                    v-model="apiConfig.summaryLlmModel" 
                    placeholder="请输入模型名称" 
              class="input-field">
            </el-input>
          </el-form-item>
            
                <el-form-item label="接口类型" v-if="apiConfig.summaryLlmType === 'custom'">
                  <el-select v-model="apiConfig.summaryLlmInterfaceType" placeholder="请选择接口类型" class="full-width">
                <el-option label="自动检测" value="auto">
                  <span class="option-content">
                    自动检测
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口(/v1/chat/completions)" value="openai">
                  <span class="option-content">
                    OpenAI兼容接口(/v1/chat/completions)
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口(/v1/completions)" value="openai_completions" disabled>
                  <span class="option-content">
                    OpenAI兼容接口(/v1/completions)
                  </span>
                </el-option>
                <el-option label="Anthropic兼容接口" value="anthropic">
                  <span class="option-content">
                    Anthropic兼容接口
                  </span>
                </el-option>
                <el-option label="自定义OpenAI兼容接口" value="custom">
                  <span class="option-content">
                    自定义OpenAI兼容接口
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip"><i class="el-icon-info"></i>{{ getLlmInterfaceTip(apiConfig.summaryLlmInterfaceType) }}</div>
          </el-form-item>
            
            <el-form-item label="API接口地址">
                  <el-input v-model="apiConfig.summaryLlmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>
            </el-form-item>

                <el-form-item label="接口平台">
                  <el-select v-model="apiConfig.summaryLlmThinkingProfile" placeholder="自动识别" class="full-width">
                    <el-option label="自动识别" value="auto"></el-option>
                    <el-option label="OpenRouter" value="openrouter"></el-option>
                    <el-option label="WorldRouter" value="worldrouter"></el-option>
                    <el-option label="硅基流动" value="siliconflow"></el-option>
                    <el-option label="DeepSeek 官方" value="deepseek_official"></el-option>
                    <el-option label="OpenAI" value="openai"></el-option>
                    <el-option label="Anthropic" value="anthropic"></el-option>
                    <el-option label="通用 OpenAI 兼容" value="generic_openai_compatible"></el-option>
                  </el-select>
                  <div class="form-tip"><i class="el-icon-info"></i>用于摘要独立模型的接口平台识别。</div>
                </el-form-item>
                <el-form-item label="自定义请求参数">
                  <el-input v-model="apiConfig.summaryLlmThinkingExtraBodyText" type="textarea" :rows="3" placeholder='例如：{"thinking_budget":1024}' class="textarea-field"></el-input>
                  <div class="form-tip"><i class="el-icon-info"></i>JSON 对象。系统生成的平台参数会覆盖同名字段。根据实际需要填写，可不填。</div>
                </el-form-item>
            
                <el-form-item label="API密钥">
                  <el-input v-model="apiConfig.summaryLlmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field" @input="cancelSecretClear('summary')">
              <template slot="prefix">
                <i class="el-icon-lock"></i>
              </template>
            </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                    <template v-if="apiConfig.clearExistingSummaryLlmKey">
                      保存后将清除已保存密钥
                </template>
                    <template v-else-if="apiConfig.hasExistingSummaryLlmKey">
                      已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                </template>
                <template v-else>
                      API密钥将自动加密存储，确保您的数据安全
                </template>
              </div>
              <div v-if="apiConfig.hasExistingSummaryLlmKey || apiConfig.clearExistingSummaryLlmKey" class="secret-actions">
                <el-button v-if="apiConfig.hasExistingSummaryLlmKey" type="text" size="mini" @click="markSecretForClear('summary')">
                  清除已保存密钥
                </el-button>
                <el-button v-if="apiConfig.clearExistingSummaryLlmKey" type="text" size="mini" @click="cancelSecretClear('summary', true)">
                  撤销清除
                </el-button>
              </div>
          </el-form-item>
            
          <el-form-item label="超时时间">
              <div class="timeout-group">
                    <el-input v-model.number="apiConfig.summaryLlmTimeout" placeholder="请输入超时时间" class="timeout-input">
                  <template slot="append">秒</template>
                </el-input>
              </div>
          </el-form-item>

          <el-form-item label="Max Tokens">
            <el-input
              v-model="apiConfig.summaryLlmMaxTokens"
              inputmode="numeric"
              placeholder="最大生成令牌数"
              class="input-field"
              @input="sanitizeMaxTokensField('summaryLlmMaxTokens', $event)"
              @blur="normalizeMaxTokensField('summaryLlmMaxTokens')">
              <template slot="append">tokens</template>
            </el-input>
            <div class="form-tip"><i class="el-icon-info"></i>最大生成令牌数，默认1000</div>
          </el-form-item>

              <el-form-item label="思考程度">
                <el-select v-model="apiConfig.summaryLlmReasoningEffort" clearable placeholder="不传入" class="full-width">
                  <el-option label="低" value="low"></el-option>
                  <el-option label="中" value="medium"></el-option>
                  <el-option label="高" value="high"></el-option>
                  <el-option label="超高" value="xhigh"></el-option>
                </el-select>
                <div class="form-tip"><i class="el-icon-info"></i>仅在当前摘要独立模型支持思考参数时传入。</div>
              </el-form-item>

              <el-form-item label="Temperature（可选）">
                <el-input-number v-model="apiConfig.summaryLlmTemperature" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>控制输出随机性（0-2），默认0.7</div>
              </el-form-item>

              <el-form-item label="Top P（可选）">
                <el-input-number v-model="apiConfig.summaryLlmTopP" :min="0" :max="1" :step="0.01" :precision="2" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>核采样参数（0-1），默认1.0</div>
              </el-form-item>

              <el-form-item label="频率惩罚（可选）">
                <el-input-number v-model="apiConfig.summaryLlmFrequencyPenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>降低重复词汇频率（0-2），默认0</div>
              </el-form-item>

              <el-form-item label="存在惩罚（可选）">
                <el-input-number v-model="apiConfig.summaryLlmPresencePenalty" :min="0" :max="2" :step="0.1" :precision="1" class="input-field"></el-input-number>
                <div class="form-tip"><i class="el-icon-info"></i>鼓励谈论新话题（0-2），默认0</div>
              </el-form-item>
              </template>
              
              <el-form-item label="摘要风格">
                <el-select v-model="apiConfig.summaryStyle" placeholder="请选择摘要风格" class="full-width">
                  <el-option label="简洁明了" value="concise">
                    <span class="option-content">
                      <i class="el-icon-document-copy"></i>
                      简洁明了
                    </span>
                  </el-option>
                  <el-option label="详细描述" value="detailed">
                    <span class="option-content">
                      <i class="el-icon-reading"></i>
                      详细描述
                    </span>
                  </el-option>
                  <el-option label="学术风格" value="academic">
                    <span class="option-content">
                      <i class="el-icon-notebook-2"></i>
                      学术风格
                    </span>
                  </el-option>
                </el-select>

              </el-form-item>
              
              <el-form-item label="摘要长度">
                <el-input-number 
                  v-model="apiConfig.summaryMaxLength" 
                  :min="50" 
                  :max="500" 
                  :step="10"
                  placeholder="请输入摘要最大长度"
                  class="number-input">
                </el-input-number>

              </el-form-item>
              
              <el-form-item label="摘要提示词">
                <el-input 
                  type="textarea" 
                  v-model="apiConfig.summaryPrompt" 
                  :rows="3" 
                  placeholder="请输入摘要生成的提示词，用于指导AI如何生成摘要"
                  class="textarea-field">
                </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  系统已支持输入输出TOON/JSON/CSV格式, 具体格式由提示词指定, 后端会自动解析; 可使用占位符：{style_desc}风格描述，{max_length}最大长度，{toon_example}TOON格式示例（token更省），{json_example}JSON格式示例（简洁直观），{csv_example}CSV格式示例，{source_content}源语言内容，{source_lang}源语言名称，{languages}目标语言列表。AI模式只传源语言内容，让AI翻译生成各语言摘要
                  <a href="javascript:void(0)" @click="showPromptDialog('toon', 'summary')" style="margin-left: 6px;">TOON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('json', 'summary')" style="margin-left: 6px;">JSON格式提示词</a>
                  <a href="javascript:void(0)" @click="showPromptDialog('csv', 'summary')" style="margin-left: 6px;">CSV格式提示词</a>
                </div>
              </el-form-item>
            </template>
            
            <!-- 测试摘要按钮 -->
            <el-form-item v-if="apiConfig.summaryMode !== 'disabled'" label=" " style="margin-top: 20px;">
              <el-button type="success" @click="testSummary" class="action-btn success-btn" :loading="testSummaryLoading">
                <i class="el-icon-link"></i>
                {{ apiConfig.summaryMode === 'textrank' ? '测试本地摘录' : '测试摘要' }}
              </el-button>
            </el-form-item>
          </div>
        </div>

        <!-- AI 生图功能配置 -->
        <div class="config-section">
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
              AI 生图功能配置
            </el-tag>
          </div>
          <div class="section-content">
            <el-form-item label="生图模式">
              <el-select v-model="apiConfig.imageMode" placeholder="请选择生图模式" style="width: 220px" class="mrb10">
                <el-option key="disabled" label="关闭生图" :value="'disabled'">
                  <span class="option-content">
                    <i class="el-icon-remove-outline"></i>
                    关闭生图
                  </span>
                </el-option>
                <el-option key="global" label="使用全局AI模型提炼" :value="'global'">
                  <span class="option-content">
                    <i class="el-icon-s-grid"></i>
                    使用全局AI模型提炼
                  </span>
                </el-option>
                <el-option key="dedicated" label="使用独立AI模型提炼" :value="'dedicated'">
                  <span class="option-content">
                    <i class="el-icon-setting"></i>
                    使用独立AI模型提炼
                  </span>
                </el-option>
                <el-option key="plain" label="直接拼接（不用AI提炼）" :value="'plain'">
                  <span class="option-content">
                    <i class="el-icon-document"></i>
                    直接拼接（不用AI提炼）
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.imageMode === 'disabled'">
                  关闭 AI 生图功能，文章编辑器中不显示生成封面按钮
                </template>
                <template v-else-if="apiConfig.imageMode === 'plain'">
                  使用封面模板默认值拼接 prompt，主体取文章标题/内容，不经过 AI 提炼，速度快、零 token 消耗
                </template>
                <template v-else-if="apiConfig.imageMode === 'global'">
                  使用上方配置的全局 AI 模型提炼生图 prompt，效果好，需要 API 密钥
                </template>
                <template v-else-if="apiConfig.imageMode === 'dedicated'">
                  为生图功能配置独立的 AI 模型来提炼 prompt，可以使用不同的模型和密钥
                </template>
              </div>
            </el-form-item>

            <template v-if="apiConfig.imageMode !== 'disabled'">
              <!-- 生图服务商配置 -->
              <el-form-item label="生图服务商">
                <el-select v-model="apiConfig.imageProvider" @change="onImageProviderChange" placeholder="请选择生图服务商" class="full-width">
                  <el-option label="OpenAI (gpt-image-2)" value="openai">
                    <span class="option-content">OpenAI (gpt-image-2)</span>
                  </el-option>
                  <el-option label="硅基流动 SiliconFlow" value="siliconflow">
                    <span class="option-content">硅基流动 SiliconFlow</span>
                  </el-option>
                  <el-option label="豆包/火山 (Seedream 5.0)" value="doubao">
                    <span class="option-content">豆包/火山 (Seedream 5.0)</span>
                  </el-option>
                  <el-option label="通义万相 (Wan 2.7)" value="dashscope">
                    <span class="option-content">通义万相 (Wan 2.7)</span>
                  </el-option>
                  <el-option label="Google Gemini (Nano Banana Pro)" value="gemini">
                    <span class="option-content">Google Gemini (Nano Banana Pro)</span>
                  </el-option>
                  <el-option label="自定义兼容端点" value="custom">
                    <span class="option-content">自定义兼容端点</span>
                  </el-option>
                </el-select>
              </el-form-item>

              <el-form-item label="模型名称">
                <el-input v-model="apiConfig.imageModel" placeholder="请输入生图模型名称" class="input-field"></el-input>
              </el-form-item>

              <el-form-item label="API接口地址">
                <el-input v-model="apiConfig.imageUrl" placeholder="请输入生图API接口地址" class="input-field"></el-input>
              </el-form-item>

              <el-form-item label="API密钥">
                <el-input v-model="apiConfig.imageApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field" @input="cancelSecretClear('image')">
                  <template slot="prefix">
                    <i class="el-icon-lock"></i>
                  </template>
                </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.clearExistingImageKey">
                    保存后将清除已保存密钥
                  </template>
                  <template v-else-if="apiConfig.hasExistingImageKey">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                    API密钥将自动加密存储，确保您的数据安全
                  </template>
                </div>
                <div v-if="apiConfig.hasExistingImageKey || apiConfig.clearExistingImageKey" class="secret-actions">
                  <el-button v-if="apiConfig.hasExistingImageKey" type="text" size="mini" @click="markSecretForClear('image')">
                    清除已保存密钥
                  </el-button>
                  <el-button v-if="apiConfig.clearExistingImageKey" type="text" size="mini" @click="cancelSecretClear('image', true)">
                    撤销清除
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item label="图片尺寸">
                <el-select v-model="apiConfig.imageSize" placeholder="请选择宽高比" class="full-width" @change="onImageSizeChange">
                  <el-option label="1:1（正方形）" value="1:1"></el-option>
                  <el-option label="16:9（横向宽屏）" value="16:9"></el-option>
                  <el-option label="9:16（纵向竖屏）" value="9:16"></el-option>
                  <el-option label="4:3（横向标准）" value="4:3"></el-option>
                  <el-option label="3:4（纵向标准）" value="3:4"></el-option>
                </el-select>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  宽高比，所有服务商通用。Gemini 直接使用此比例；其他服务商需在下方"分辨率"中填写匹配此比例的像素
                </div>
              </el-form-item>

              <el-form-item v-if="apiConfig.imageProvider !== 'gemini'" label="分辨率" :error="imageResolutionError">
                <el-input
                  v-model="apiConfig.imageResolution"
                  placeholder="如 1920x1080"
                  @blur="validateImageResolution"
                  class="resolution-input"
                >
                  <template slot="prepend">宽x高</template>
                </el-input>
                <div class="resolution-presets">
                  <span class="presets-label">常用：</span>
                  <el-tag
                    v-for="opt in imageResolutionPresets"
                    :key="opt"
                    :type="apiConfig.imageResolution === opt ? 'primary' : 'info'"
                    size="mini"
                    class="preset-tag"
                    @click="apiConfig.imageResolution = opt"
                  >{{ opt }}</el-tag>
                </div>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  仅对 OpenAI / SiliconFlow / 豆包 / 通义万相 / 自定义 生效。须与上方宽高比一致，可自由输入任意像素值（如 4K）
                </div>
              </el-form-item>

              <el-form-item v-if="apiConfig.imageProvider === 'openai' || apiConfig.imageProvider === 'custom'" label="图片质量">
                <el-select v-model="apiConfig.imageQuality" placeholder="请选择图片质量" class="full-width">
                  <el-option label="自动" value="auto"></el-option>
                  <el-option label="低" value="low"></el-option>
                  <el-option label="中" value="medium"></el-option>
                  <el-option label="高" value="high"></el-option>
                </el-select>
              </el-form-item>

              <el-form-item label="超时时间">
                <div class="timeout-group">
                  <el-input v-model.number="apiConfig.imageTimeout" placeholder="请输入超时时间" class="timeout-input">
                    <template slot="append">秒</template>
                  </el-input>
                </div>
              </el-form-item>

              <!-- 真实感封面模板 -->
              <el-form-item label="封面模板">
                <el-select v-model="apiConfig.coverTemplate" placeholder="请选择封面模板" class="full-width">
                  <el-option
                    v-for="opt in coverTemplateOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value">
                    <span class="option-content">{{ opt.label }}</span>
                  </el-option>
                </el-select>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <span v-if="apiConfig.coverTemplate === 'object'">用物理材质+摄影参数限制AI发散，极致真实摄影感。材质/镜头/光影等全部由 AI 根据文章内容提炼，无需手动选择</span>
                  <span v-else-if="apiConfig.coverTemplate === 'portrait'">真实感人像摄影封面。人物特征/情绪/穿搭/镜头/光线等全部由 AI 根据文章内容提炼，无需手动选择</span>
                  <span v-else-if="apiConfig.coverTemplate === 'felt'">羊毛毡手工质感Q版可爱风。主体Q版化、材质/造型/光影等全部由 AI 根据文章内容提炼，生成蓬松治愈的毛毡插画</span>
                  <span v-else-if="apiConfig.coverTemplate === 'cyberpunk'">赛博朋克霓虹未来风。主体科技改造化、材质/镜头/光影等全部由 AI 根据文章内容提炼，生成雨夜霓虹质感封面</span>
                  <span v-else-if="apiConfig.coverTemplate === 'watercolor'">水彩手绘风。色彩/笔触/构图等全部由 AI 根据文章内容提炼，生成通透晕染的文艺清新插画</span>
                  <span v-else-if="apiConfig.coverTemplate === 'ink'">国风水墨画。笔墨/章法/意境等全部由 AI 根据文章内容提炼，生成留白禅意的传统水墨画</span>
                  <span v-else-if="apiConfig.coverTemplate === 'pixel'">像素复古风。像素细节/色板/构图等全部由 AI 根据文章内容提炼，生成 8-bit 复古游戏画面</span>
                  <span v-else-if="apiConfig.coverTemplate === '3d'">3D渲染卡通风。材质/布光/构图等全部由 AI 根据文章内容提炼，生成圆润精致的 3D 渲染插画</span>
                  <span v-else-if="apiConfig.coverTemplate === 'minimal'">极简几何风。几何形态/配色/构图等全部由 AI 根据文章内容提炼，生成克制的现代设计海报</span>
                  <span v-else-if="apiConfig.coverTemplate === 'collage'">复古拼贴风。拼贴元素/材质/层次等全部由 AI 根据文章内容提炼，生成怀旧文艺的杂志拼贴画</span>
                  <span v-else-if="apiConfig.coverTemplate === 'custom'">完全自定义：在下方填写你的 LLM 系统提示词，AI 将按你的公式根据文章内容提炼生图 prompt</span>
                </div>
                <div class="form-tip" v-if="apiConfig.imageMode === 'plain'">
                  <i class="el-icon-warning-outline" style="color: #E6A23C"></i>
                  <span style="color: #E6A23C">当前为"直接拼接"模式，不调用 AI 提炼，模板将使用文章标题/内容作为主体描述，材质/镜头/光影等使用预设默认值。建议切换到 global/dedicated 模式以获得最佳真实感效果</span>
                </div>
              </el-form-item>

              <!-- 自定义模板：填写 LLM 系统提示词 -->
              <el-form-item v-if="apiConfig.coverTemplate === 'custom'" label="自定义提示词公式">
                <el-input
                  type="textarea"
                  :rows="8"
                  v-model="apiConfig.customRefinePrompt"
                  :placeholder="customRefinePromptPlaceholder"
                  resize="vertical">
                </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <span>此提示词将作为 LLM 的 system 指令，用于指导 AI 根据文章内容生成生图 prompt。建议包含：风格设定、核心主体提取规则、材质/镜头/光影要求、输出格式约束等。留空时将降级为物品类模板</span>
                </div>
              </el-form-item>



              <!-- 使用全局AI模型 -->
              <template v-if="apiConfig.imageMode === 'global'">
                <el-alert title="将使用上方配置的全局AI模型提炼生图prompt" type="success" :closable="false" show-icon style="margin: 10px; margin-bottom: 20px;"></el-alert>
              </template>

              <!-- 使用独立AI模型 -->
              <template v-if="apiConfig.imageMode === 'dedicated'">
                <el-alert title="为生图功能配置独立的AI模型来提炼prompt" type="info" :closable="false" show-icon style="margin: 10px; margin-bottom: 20px;"></el-alert>

                <el-form-item label="大模型类型">
                  <el-select v-model="apiConfig.imageLlmType" @change="onImageLlmTypeChange" placeholder="请选择大模型类型" class="full-width">
                    <el-option label="OpenAI / ChatGPT API" value="openai"></el-option>
                    <el-option label="Anthropic (Claude)" value="anthropic"></el-option>
                    <el-option label="硅基流动" value="siliconflow"></el-option>
                    <el-option label="DeepSeek" value="deepseek"></el-option>
                    <el-option label="OpenRouter" value="openrouter"></el-option>
                    <el-option label="WorldRouter" value="worldrouter"></el-option>
                    <el-option label="Azure OpenAI" value="azure"></el-option>
                    <el-option label="自定义/其他" value="custom"></el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="模型名称">
                  <el-input v-model="apiConfig.imageLlmModel" placeholder="请输入模型名称" class="input-field"></el-input>
                </el-form-item>

                <el-form-item label="接口类型" v-if="apiConfig.imageLlmType === 'custom'">
                  <el-select v-model="apiConfig.imageLlmInterfaceType" placeholder="请选择接口类型" class="full-width">
                    <el-option label="自动检测" value="auto"></el-option>
                    <el-option label="OpenAI兼容接口(/v1/chat/completions)" value="openai"></el-option>
                    <el-option label="Anthropic兼容接口" value="anthropic"></el-option>
                    <el-option label="自定义OpenAI兼容接口" value="custom"></el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="API接口地址">
                  <el-input v-model="apiConfig.imageLlmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>
                </el-form-item>

                <el-form-item label="API密钥">
                  <el-input v-model="apiConfig.imageLlmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field" @input="cancelSecretClear('imageLlm')">
                    <template slot="prefix">
                      <i class="el-icon-lock"></i>
                    </template>
                  </el-input>
                  <div class="form-tip">
                    <i class="el-icon-info"></i>
                    <template v-if="apiConfig.clearExistingImageLlmKey">
                      保存后将清除已保存密钥
                    </template>
                    <template v-else-if="apiConfig.hasExistingImageLlmKey">
                      已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                    </template>
                    <template v-else>
                      API密钥将自动加密存储，确保您的数据安全
                    </template>
                  </div>
                  <div v-if="apiConfig.hasExistingImageLlmKey || apiConfig.clearExistingImageLlmKey" class="secret-actions">
                    <el-button v-if="apiConfig.hasExistingImageLlmKey" type="text" size="mini" @click="markSecretForClear('imageLlm')">
                      清除已保存密钥
                    </el-button>
                    <el-button v-if="apiConfig.clearExistingImageLlmKey" type="text" size="mini" @click="cancelSecretClear('imageLlm', true)">
                      撤销清除
                    </el-button>
                  </div>
                </el-form-item>

                <el-form-item label="超时时间">
                  <div class="timeout-group">
                    <el-input v-model.number="apiConfig.imageLlmTimeout" placeholder="请输入超时时间" class="timeout-input">
                      <template slot="append">秒</template>
                    </el-input>
                  </div>
                </el-form-item>
              </template>

              <!-- 测试生图按钮 -->
              <el-form-item label=" " style="margin-top: 20px;">
                <el-button type="success" @click="testImage" class="action-btn success-btn" :loading="testImageLoading">
                  <i class="el-icon-magic-stick"></i>
                  测试生图
                </el-button>
              </el-form-item>
            </template>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button type="primary" @click="saveApiConfig" class="action-btn primary-btn">
            <i class="el-icon-check"></i>
            保存配置
          </el-button>
          <el-button @click="getApiConfig" class="action-btn">
            <i class="el-icon-refresh"></i>
            刷新配置
          </el-button>
        </div>
      </el-form>
    </div>
    
    <!-- 测试翻译对话框 -->
    <el-dialog :visible.sync="testTranslationDialogVisible" width="65%" custom-class="test-dialog">
      <div slot="title" class="dialog-title-custom">
        <span class="title-text">测试翻译</span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-tag size="small" type="info" effect="plain">
            {{ getLanguageName(apiConfig.defaultSourceLang) }} → {{ getLanguageName(apiConfig.defaultTargetLang) }}
          </el-tag>
          <el-tag size="small" type="success" effect="plain">
            <i class="el-icon-data-analysis"></i>
            格式优化 · token优化
          </el-tag>
        </div>
      </div>
      
      <div class="dialog-content" ref="dialogContent">
        <div class="test-form">
          <el-tabs v-model="testTranslationForm.testType" type="border-card">
            <el-tab-pane label="文章翻译（格式自动解析）" name="toon">
              <div class="toon-hint">
                <i class="el-icon-info"></i>
                标题和内容将按提示词指定的TOON/JSON/CSV格式一次性翻译，系统会显示相对传统JSON的长度节省估算
              </div>
              
              <div class="input-section">
                <label>文章标题</label>
                <el-input v-model="testTranslationForm.title" placeholder="如：人工智能的未来发展"></el-input>
              </div>
              
              <div class="input-section">
                <label>文章内容</label>
                <el-input 
                  type="textarea" 
                  v-model="testTranslationForm.content" 
                  :rows="8" 
                  placeholder="支持Markdown格式&#10;示例：&#10;# 标题&#10;## 副标题&#10;内容..."
                  class="source-input">
                </el-input>
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="单文本翻译" name="single">
              <div class="input-section">
                <label>源文本</label>
                <el-input 
                  type="textarea" 
                  v-model="testTranslationForm.sourceText" 
                  :rows="6" 
                  placeholder="请输入要翻译的文本"
                  class="source-input">
                </el-input>
              </div>
            </el-tab-pane>
          </el-tabs>
          
          <div class="translate-section">
            <el-button type="success" @click="doTestTranslation" :loading="testTranslationLoading" class="translate-btn" style="min-width: 120px;">
              <svg viewBox="0 0 1024 1024" width="16" height="16" style="vertical-align: -2px; margin-right: 4px;">
                <path d="M213.333333 640v85.333333a85.333333 85.333333 0 0 0 78.933334 85.12L298.666667 810.666667h128v85.333333H298.666667a170.666667 170.666667 0 0 1-170.666667-170.666667v-85.333333h85.333333z m554.666667-213.333333l187.733333 469.333333h-91.946666l-51.242667-128h-174.506667l-51.157333 128h-91.904L682.666667 426.666667h85.333333z m-42.666667 123.093333L672.128 682.666667h106.325333L725.333333 549.76zM341.333333 85.333333v85.333334h170.666667v298.666666H341.333333v128H256v-128H85.333333V170.666667h170.666667V85.333333h85.333333z m384 42.666667a170.666667 170.666667 0 0 1 170.666667 170.666667v85.333333h-85.333333V298.666667a85.333333 85.333333 0 0 0-85.333334-85.333334h-128V128h128zM256 256H170.666667v128h85.333333V256z m170.666667 0H341.333333v128h85.333334V256z" fill="currentColor"></path>
              </svg>
              {{ testTranslationLoading ? '翻译中...' : '开始翻译' }}
            </el-button>
          </div>
          
          <!-- 文章结构化翻译结果 -->
          <template v-if="testTranslationForm.testType === 'toon' && (testTranslationForm.translatedTitle || testTranslationForm.translatedContent)">
            <div class="result-section">
              <label>翻译后的标题</label>
              <el-input v-model="testTranslationForm.translatedTitle" readonly class="result-output"></el-input>
            </div>
            
            <div class="result-section">
              <label>翻译后的内容</label>
              <el-input type="textarea" v-model="testTranslationForm.translatedContent" :rows="6" readonly class="result-output"></el-input>
            </div>
            
            <div class="result-meta">
              <el-tag size="small" type="primary" v-if="testTranslationForm.inputFormat">
                <i class="el-icon-document"></i>
                输入: {{ getDataFormatLabel(testTranslationForm.inputFormat) }}
              </el-tag>
              <el-tag size="small" type="primary" v-if="testTranslationForm.responseFormat">
                <i class="el-icon-finished"></i>
                返回: {{ getDataFormatLabel(testTranslationForm.responseFormat) }}
              </el-tag>
              <el-tag size="small" type="info" v-if="testTranslationForm.formatTokens">
                <i class="el-icon-s-data"></i>
                格式长度: {{ testTranslationForm.formatTokens }} 字符
              </el-tag>
              <el-tag size="small" type="success" v-if="testTranslationForm.tokenSavedPercent">
                <i class="el-icon-data-analysis"></i>
                较{{ testTranslationForm.tokenBaselineLabel || '传统JSON' }}估算节省: {{ testTranslationForm.tokenSavedPercent }}%
              </el-tag>
              <el-tag size="small" type="info" v-if="testTranslationForm.processingTime">
                <i class="el-icon-time"></i>
                用时: {{ testTranslationForm.processingTime.toFixed(2) }}秒
              </el-tag>
            </div>
          </template>
          
          <!-- 单文本翻译结果 -->
          <div class="result-section" v-else-if="testTranslationForm.translatedText">
            <label>翻译结果</label>
            <el-input type="textarea" v-model="testTranslationForm.translatedText" :rows="4" readonly class="result-output"></el-input>
            <div class="result-meta" v-if="testTranslationForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testTranslationForm.processingTime.toFixed(2) }}秒
              </el-tag>
            </div>
          </div>
          
          <div class="error-section" v-if="testTranslationForm.error">
            <label>错误信息</label>
            <el-alert
              :title="testTranslationForm.error"
              type="error"
              show-icon>
            </el-alert>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testTranslationDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 提示词模板对话框 -->
    <el-dialog :visible.sync="promptDialogVisible" width="55%" custom-class="test-dialog" :title="promptDialogTitle">
      <div class="dialog-content">
        <div class="test-form">
          <div class="input-section">
            <label>{{ promptDialogFeature === 'summary' ? '摘要' : '翻译' }} {{ promptDialogFormatLabel }} 格式提示词模板</label>
            <el-input
              type="textarea"
              v-model="currentPromptTemplate"
              :rows="12"
              readonly
              class="source-input"
              style="font-family: monospace;">
            </el-input>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="promptDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 生图测试对话框 -->
    <el-dialog :visible.sync="testImageDialogVisible" width="70%" custom-class="test-dialog" :close-on-click-modal="false">
      <div slot="title" class="dialog-title-custom">
        <span class="title-text">测试 AI 生图</span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-tag size="small" type="info" effect="plain">
            {{ apiConfig.imageProvider }} / {{ apiConfig.imageModel || '未配置模型' }}
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">
            {{ {disabled:'已关闭', plain:'纯文本拼接', global:'全局LLM提炼', dedicated:'独立LLM提炼'}[apiConfig.imageMode] || apiConfig.imageMode }}
          </el-tag>
        </div>
      </div>

      <div class="dialog-content">
        <div class="test-form">
          <div class="toon-hint">
            <i class="el-icon-info"></i>
            填入文章标题和内容，系统将按当前生图配置走完整流程（含 prompt 提炼）生成封面图，用于评估模型效果
          </div>

          <div class="input-section">
            <label>文章标题</label>
            <el-input v-model="testImageForm.title" placeholder="如：人工智能的未来发展"></el-input>
          </div>

          <div class="input-section">
            <label>文章内容</label>
            <el-input
              type="textarea"
              v-model="testImageForm.content"
              :rows="6"
              placeholder="支持Markdown/HTML格式"
              class="source-input">
            </el-input>
          </div>

          <div class="translate-section">
            <el-button type="success" @click="doTestImage" :loading="testImageLoading" class="translate-btn" style="min-width: 120px;">
              <i class="el-icon-magic-stick"></i>
              {{ testImageLoading ? '生成中...' : '生成封面图' }}
            </el-button>
          </div>

          <!-- 生图结果 -->
          <template v-if="testImageForm.imageUrl">
            <div class="result-section">
              <label>生成结果</label>
              <div class="image-preview-wrap">
                <el-image
                  :src="testImageForm.imageUrl"
                  :preview-src-list="[testImageForm.imageUrl]"
                  fit="contain"
                  class="image-preview">
                </el-image>
                <div class="image-actions">
                  <el-button size="mini" type="primary" plain @click="downloadTestImage">
                    <i class="el-icon-download"></i> 下载图片
                  </el-button>
                  <el-button size="mini" type="primary" plain @click="doTestImage" :loading="testImageLoading">
                    <i class="el-icon-refresh"></i> 重新生成
                  </el-button>
                </div>
              </div>
            </div>

            <div class="result-section" v-if="testImageForm.prompt">
              <label>生图 Prompt（最终送入生图模型的提示词）</label>
              <el-input type="textarea" v-model="testImageForm.prompt" :rows="3" readonly class="result-output" style="font-family: monospace; font-size: 12px;"></el-input>
            </div>

            <div class="result-meta">
              <el-tag size="small" type="success" v-if="testImageForm.durationMs != null">
                <i class="el-icon-time"></i> 耗时: {{ testImageForm.durationMs }}ms
              </el-tag>
              <el-tag size="small" type="info" v-if="testImageForm.form">
                <i class="el-icon-files"></i> 来源: {{ testImageForm.form === 'url' ? 'URL下载' : '直接字节' }}
              </el-tag>
              <el-tag size="small" type="primary" v-if="testImageForm.provider">
                {{ testImageForm.provider }}
              </el-tag>
            </div>
          </template>

          <div class="error-section" v-if="testImageForm.error">
            <label>错误信息</label>
            <el-alert
              :title="testImageForm.error"
              type="error"
              show-icon
              :closable="false">
            </el-alert>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testImageDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 摘要测试对话框 -->
    <el-dialog :visible.sync="testSummaryDialogVisible" width="70%" custom-class="test-dialog">
      <div slot="title" class="dialog-title-custom">
        <span class="title-text">测试摘要</span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-tag size="small" type="info" effect="plain">
            {{ apiConfig.summaryStyle === 'concise' ? '简洁明了' : 
               apiConfig.summaryStyle === 'detailed' ? '详细描述' : '学术风格' }}
          </el-tag>
          <el-tag size="small" type="success" effect="plain">
            <i class="el-icon-document"></i>
            最大 {{ apiConfig.summaryMaxLength }} 字符
          </el-tag>
        </div>
      </div>
      
      <div class="dialog-content">
        <div class="test-form">
          <div class="input-section">
            <label>测试内容</label>
            <el-input 
              type="textarea" 
              v-model="testSummaryForm.content" 
              :rows="8" 
              placeholder="请输入要生成摘要的文章内容，支持Markdown格式"
              class="source-input">
            </el-input>
            <div class="input-tips">
              <el-tag size="mini" type="warning">提示</el-tag>
              <span>建议输入至少500字符的内容以获得更好的摘要效果</span>
            </div>
          </div>
          
          <div class="test-section">
            <el-button 
              type="primary" 
              @click="doTestSummary" 
              :loading="testSummaryLoading" 
              class="test-btn">
              <i class="el-icon-magic-stick"></i>
              {{ testSummaryLoading ? '生成中...' : '生成摘要' }}
            </el-button>
          </div>
          
          <div class="result-section" v-if="testSummaryForm.summaries">
            <label>生成的多语言摘要</label>
            <div v-for="(summary, langCode) in testSummaryForm.summaries" :key="langCode" style="margin-bottom: 15px;">
              <div style="margin-bottom: 5px;">
                <el-tag size="small" type="primary">
                  <i class="el-icon-chat-dot-round"></i>
                  {{ getLanguageName(langCode) }}
                </el-tag>
                <el-tag size="small" type="warning" style="margin-left: 5px;">
                  {{ summary.length }}字符
                </el-tag>
              </div>
              <el-input 
                type="textarea" 
                :value="summary" 
                :rows="3" 
                readonly 
                class="result-output">
              </el-input>
            </div>
            <div class="result-meta" v-if="testSummaryForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testSummaryForm.processingTime }}秒
              </el-tag>
              <el-tag size="small" type="primary" v-if="testSummaryForm.toonTokens">
                <i class="el-icon-s-data"></i>
                消耗: {{ testSummaryForm.toonTokens }} tokens
              </el-tag>
              <el-tag size="small" type="warning" v-if="testSummaryForm.tokenSavedPercent">
                <i class="el-icon-data-analysis"></i>
                节省: {{ testSummaryForm.tokenSavedPercent }}%
              </el-tag>
              <el-tag size="small" type="success" v-if="testSummaryForm.method">
                <i class="el-icon-cpu"></i>
                方法: {{ 
                  testSummaryForm.method === 'ai-openai' ? 'OpenAI' :
                  testSummaryForm.method === 'ai-anthropic' ? 'Claude' :
                  testSummaryForm.method === 'ai-siliconflow' ? '硅基流动' :
                  testSummaryForm.method === 'ai-custom' ? '自定义AI' :
                  testSummaryForm.method === 'llm' ? 'AI模型' :
                  testSummaryForm.method === 'textrank' ? '本地摘录' :
                  testSummaryForm.method === 'local-excerpt' ? '本地摘录' :
                  testSummaryForm.method 
                }}
              </el-tag>
              <el-tag size="small" type="primary">
                <i class="el-icon-files"></i>
                语言数: {{ Object.keys(testSummaryForm.summaries).length }}
              </el-tag>
            </div>
          </div>
          
          <div class="error-section" v-if="testSummaryForm.error">
            <label>错误信息</label>
            <el-alert
              :title="testSummaryForm.error"
              type="error"
              show-icon>
            </el-alert>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testSummaryDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="resetSummaryTest">重新测试</el-button>
      </div>
    </el-dialog>
    
    <!-- 本地摘录测试对话框 -->
    <el-dialog :visible.sync="testTextrankDialogVisible" width="75%" custom-class="test-dialog">
      <div slot="title" class="dialog-title-custom">
        <span class="title-text">测试本地摘录</span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-tag size="small" type="warning" effect="plain">
            <i class="el-icon-cpu"></i>
            双语言独立处理
          </el-tag>
          <el-tag size="small" type="info" effect="plain">
            {{ getLanguageName(apiConfig.defaultSourceLang) }} / {{ getLanguageName(apiConfig.defaultTargetLang) }}
          </el-tag>
        </div>
      </div>
      
      <div class="dialog-content">
        <div class="test-form">
          <div class="dual-input-section">
            <div class="input-column">
              <label>{{ getLanguageName(apiConfig.defaultSourceLang) }}内容</label>
              <el-input 
                type="textarea" 
                v-model="testTextrankForm.sourceContent" 
                :rows="10" 
                :placeholder="`请输入${getLanguageName(apiConfig.defaultSourceLang)}文章内容`"
                class="source-input">
              </el-input>
              <div class="char-count">
                字符数: {{ testTextrankForm.sourceContent.length }}
              </div>
            </div>
            
            <div class="input-column">
              <label>{{ getLanguageName(apiConfig.defaultTargetLang) }}内容</label>
              <el-input 
                type="textarea" 
                v-model="testTextrankForm.targetContent" 
                :rows="10" 
                :placeholder="`请输入${getLanguageName(apiConfig.defaultTargetLang)}文章内容`"
                class="source-input">
              </el-input>
              <div class="char-count">
                字符数: {{ testTextrankForm.targetContent.length }}
              </div>
            </div>
          </div>
          
          <div class="input-tips" style="margin-top: 10px;">
            <el-tag size="mini" type="warning">提示</el-tag>
            <span>本地算法只抽取/拼接原文片段，不理解上下文，建议仅作为展示摘录兜底</span>
          </div>
          
          <div class="test-section">
            <el-button 
              type="primary" 
              @click="doTestTextrank" 
              :loading="testTextrankLoading" 
              class="test-btn">
              <i class="el-icon-magic-stick"></i>
              {{ testTextrankLoading ? '生成中...' : '生成摘录' }}
            </el-button>
          </div>
          
          <div class="result-section" v-if="testTextrankForm.summaries">
            <label>生成的多语言摘录</label>
            <div v-for="(summary, langCode) in testTextrankForm.summaries" :key="langCode" style="margin-bottom: 15px;">
              <div style="margin-bottom: 5px;">
                <el-tag size="small" type="primary">
                  <i class="el-icon-chat-dot-round"></i>
                  {{ getLanguageName(langCode) }}
                </el-tag>
                <el-tag size="small" type="warning" style="margin-left: 5px;">
                  {{ summary.length }}字符
                </el-tag>
              </div>
              <el-input 
                type="textarea" 
                :value="summary" 
                :rows="3" 
                readonly 
                class="result-output">
              </el-input>
            </div>
            <div class="result-meta" v-if="testTextrankForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testTextrankForm.processingTime }}秒
              </el-tag>
              <el-tag size="small" type="success">
                <i class="el-icon-data-analysis"></i>
                方法: 本地摘录
              </el-tag>
              <el-tag size="small" type="primary">
                <i class="el-icon-files"></i>
                语言数: {{ Object.keys(testTextrankForm.summaries).length }}
              </el-tag>
            </div>
          </div>
          
          <div class="error-section" v-if="testTextrankForm.error">
            <label>错误信息</label>
            <el-alert
              :title="testTextrankForm.error"
              type="error"
              show-icon>
            </el-alert>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testTextrankDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="resetTextrankTest">重新测试</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getAdminLanguageName, preloadLanguageMapping } from '@/utils/languageUtils';

export default {
  name: 'ArticleAiAssistant',
  data() {
    return {
      apiConfig: {
        mode: 'llm',
        provider: 'baidu',
        appId: '',
        appSecret: '',
        customUrl: '',
        customApiKey: '',
        providerRegion: '',
        providerProjectId: '',
        providerScene: '',
        providerFormat: '',
        providerModel: '',
        providerAuthType: 'token',
        endpointType: 'free',
        providerCategory: '',
        providerSessionToken: '',
        llmType: 'openai',
        llmModel: 'gpt-4o-mini',
        llmUrl: 'https://api.openai.com/v1',  // 默认OpenAI基础URL
        llmApiKey: '',
        llmPrompt: '',
        llmTimeout: 30,
        llmMaxTokens: 1000,
        llmInterfaceType: 'auto',
        llmTemperature: 0.7,
        llmTopP: 1.0,
        llmFrequencyPenalty: 0,
        llmPresencePenalty: 0,
        llmReasoningEffort: '',
        llmThinkingProfile: 'auto',
        llmThinkingExtraBodyText: '',
        // 默认语言配置
        defaultSourceLang: 'zh',
        defaultTargetLang: 'en',
        // 密钥状态标记
        hasExistingBaiduSecret: false,
        hasExistingYoudaoSecret: false,
        hasExistingCustomKey: false,
        hasExistingCustomSecret: false,  // 添加自定义HTTP接口第二密钥状态标记
        hasExistingPrimarySecret: false,
        hasExistingSecondarySecret: false,
        hasExistingSessionToken: false,
        hasExistingLlmKey: false,
        clearExistingLlmKey: false,
        // 翻译独立AI配置
        translationLlmType: 'openai',
        translationLlmModel: 'gpt-4o-mini',
        translationLlmUrl: 'https://api.openai.com/v1',  // 默认OpenAI基础URL
        translationLlmApiKey: '',
        translationLlmTimeout: 30,
        translationLlmMaxTokens: 1000,
        translationLlmInterfaceType: 'auto',
        translationLlmTemperature: 0.7,
        translationLlmTopP: 1.0,
        translationLlmFrequencyPenalty: 0,
        translationLlmPresencePenalty: 0,
        translationLlmReasoningEffort: '',
        translationLlmThinkingProfile: 'auto',
        translationLlmThinkingExtraBodyText: '',
        hasExistingTranslationLlmKey: false,
        clearExistingTranslationLlmKey: false,
        // 智能摘要配置
        summaryMode: 'disabled',  // 'disabled' 不自动生成 | 'global' 使用全局AI | 'dedicated' 使用独立AI | 'textrank' 本地摘录
        summaryStyle: 'concise',
        summaryMaxLength: 150,
        summaryPrompt: '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 保持TOON格式结构不变（2个空格缩进）\n5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请返回TOON格式的摘要，格式如下：\n{toon_example}',
        // 摘要独立AI配置
        summaryLlmType: 'openai',
        summaryLlmModel: 'gpt-4o-mini',
        summaryLlmUrl: 'https://api.openai.com/v1',  // 默认OpenAI基础URL
        summaryLlmApiKey: '',
        summaryLlmTimeout: 30,
        summaryLlmMaxTokens: 1000,
        summaryLlmInterfaceType: 'auto',
        summaryLlmTemperature: 0.7,
        summaryLlmTopP: 1.0,
        summaryLlmFrequencyPenalty: 0,
        summaryLlmPresencePenalty: 0,
        summaryLlmReasoningEffort: '',
        summaryLlmThinkingProfile: 'auto',
        summaryLlmThinkingExtraBodyText: '',
        hasExistingSummaryLlmKey: false,
        clearExistingSummaryLlmKey: false,
        // AI生图配置
        imageMode: 'disabled',  // disabled | plain | global | dedicated
        imageProvider: 'siliconflow',
        imageModel: 'Qwen/Qwen-Image',
        imageUrl: 'https://api.siliconflow.cn/v1/images/generations',
        imageApiKey: '',
        imageSize: '16:9',
        imageResolution: '1536x864',
        imageQuality: 'auto',
        // 真实感封面模板（材质/镜头/光影等由 AI 提炼，用户只需选模板类型）
        coverTemplate: 'object',  // object | portrait | felt | cyberpunk | custom
        // 自定义模板的 LLM 系统提示词（仅 coverTemplate=custom 时使用）
        customRefinePrompt: '',
        imageTimeout: 120,
        imageResolutionError: '',
        hasExistingImageKey: false,
        clearExistingImageKey: false,
        // 生图独立AI配置（仅 imageMode=dedicated 时使用，用于提炼prompt）
        imageLlmType: 'openai',
        imageLlmModel: 'gpt-4o-mini',
        imageLlmUrl: 'https://api.openai.com/v1',
        imageLlmApiKey: '',
        imageLlmTimeout: 30,
        imageLlmInterfaceType: 'auto',
        hasExistingImageLlmKey: false,
        clearExistingImageLlmKey: false
      },
      apiProviderGroups: [
        {
          label: '国内云厂商',
          options: [
            { label: '百度翻译', value: 'baidu', icon: 'el-icon-s-platform' },
            { label: '有道云翻译', value: 'youdao', icon: 'el-icon-s-platform' },
            { label: '腾讯云 TMT', value: 'tencent', icon: 'el-icon-s-platform' },
            { label: '阿里云机器翻译', value: 'aliyun', icon: 'el-icon-s-platform' },
            { label: '火山引擎机器翻译', value: 'volcengine', icon: 'el-icon-s-platform' },
            { label: '华为云 NLP', value: 'huawei', icon: 'el-icon-s-platform' }
          ]
        },
        {
          label: '国际服务商',
          options: [
            { label: 'Google Cloud Translation', value: 'google', icon: 'el-icon-s-grid' },
            { label: 'Azure AI Translator', value: 'azure_translator', icon: 'el-icon-s-grid' },
            { label: 'DeepL', value: 'deepl', icon: 'el-icon-s-grid' },
            { label: 'Amazon Translate', value: 'aws', icon: 'el-icon-s-grid' },
            { label: 'Yandex Cloud Translate', value: 'yandex', icon: 'el-icon-s-grid' }
          ]
        },
        {
          label: '自定义',
          options: [
            { label: '自定义HTTP接口', value: 'custom', icon: 'el-icon-s-custom' }
          ]
        }
      ],
      // 真实感封面模板 - 模板类型选项（材质/镜头/光影等由 AI 提炼，无需用户选择）
      coverTemplateOptions: [
        { label: '物品类真实感模板（通用，不一定有人物）', value: 'object', desc: '用物理材质+摄影参数限制AI发散，极致真实摄影感。材质/镜头/光影由 AI 提炼' },
        { label: '人物类真实感模板', value: 'portrait', desc: '真实感人像摄影，人物特征/情绪/穿搭/镜头/光线由 AI 提炼' },
        { label: '毛毡Q版可爱风模板', value: 'felt', desc: '羊毛毡手工质感Q版插画，蓬松纤维+马卡龙色系+治愈氛围' },
        { label: '赛博朋克霓虹风模板', value: 'cyberpunk', desc: '未来都市霓虹质感，拉丝金属+LED灯带+雨夜霓虹反射，前卫冷峻' },
        { label: '水彩手绘风模板', value: 'watercolor', desc: '通透水彩晕染质感，色彩温润+笔触自然+文艺清新，适合文艺/生活类博客' },
        { label: '国风水墨画模板', value: 'ink', desc: '传统水墨写意美学，枯湿浓淡+留白意境+禅意书卷气，适合文化/历史/艺术类' },
        { label: '像素复古风模板', value: 'pixel', desc: '8-bit/16-bit 复古游戏美学，方块像素+鲜明色板+怀旧乐趣，适合游戏/技术类' },
        { label: '3D渲染卡通风模板', value: '3d', desc: '圆润黏土/塑胶质感+柔和三点布光+现代精致，适合科技/产品类' },
        { label: '极简几何风模板', value: 'minimal', desc: '极简主义设计，几何抽象+高级配色+大量留白，适合设计/商务类' },
        { label: '复古拼贴风模板', value: 'collage', desc: '旧杂志拼贴美学，手撕纸边+多层叠放+怀旧文艺，适合创意/艺术类' },
        { label: '自定义模板', value: 'custom', desc: '完全自定义 LLM 系统提示词，按自己的公式生成生图 prompt' }
      ],
      // 自定义模板提示词输入框的 placeholder（用真实换行符，避免 Vue 模板不解析 &#10;）
      customRefinePromptPlaceholder: '在此填写你的 LLM 系统提示词（refine_prompt）。AI 会以此作为系统指令，根据文章标题/内容提炼生成最终的生图 prompt。\n示例：你是一个专注于水彩插画封面的AI提示词工程师。请根据文章内容生成水彩风格的生图提示词，要求...',
      // 保存后端加载的原始配置，用于智能恢复
      savedLlmConfig: null,
      savedTranslationLlmConfig: null,
      savedSummaryLlmConfig: null,
      testTranslationDialogVisible: false,
      testTranslationForm: {
        testType: 'toon',  // 'toon'(文章结构化) 或 'single'
        sourceText: '# 人工智能简介\n\n人工智能（AI）正在改变我们的生活方式。从智能助手到自动驾驶，AI技术无处不在。\n\n## 深度学习\n\n深度学习是AI的核心技术之一，它通过神经网络模拟人脑的学习过程。',
        title: '人工智能的未来发展',
        content: '# 人工智能简介\n\n人工智能（AI）正在改变我们的生活方式。从智能助手到自动驾驶，AI技术无处不在。\n\n## 深度学习\n\n深度学习是AI的核心技术之一，它通过神经网络模拟人脑的学习过程。',
        translatedText: '',
        translatedTitle: '',
        translatedContent: '',
        toonTokens: null,
        formatTokens: null,
        inputFormat: '',
        responseFormat: '',
        tokenBaselineLabel: '',
        tokenSavedPercent: null,
        processingTime: null,
        detectedLang: null,
        useStream: false,
        error: null
      },
      testTranslationLoading: false,
      promptDialogVisible: false,
      promptDialogType: 'json',
      promptDialogFeature: 'translate',
      toonPromptTemplate: '将以下TOON格式数据从{source_lang}翻译为{target_lang}。\n\n规则：\n1. 保持TOON格式结构不变（2个空格缩进）\n2. 翻译title和content的值\n3. 保持Markdown格式\n4. 只返回TOON格式数据，不添加任何解释\n\n输入TOON数据：\n{toon_data}\n\n请返回翻译后的TOON数据，格式如下：\narticle:\n  title: (翻译后的{target_lang}标题)\n  content: (翻译后的{target_lang}内容)',
      jsonPromptTemplate: '将以下JSON格式数据从{source_lang}翻译为{target_lang}。\n\n规则：\n1. 翻译title和content的值\n2. 保持Markdown格式\n3. 只返回JSON格式数据，不添加任何解释或markdown代码块标记\n\n输入JSON数据：\n{json_data}\n\n请返回翻译后的JSON数据：\n{"title":"翻译后的{target_lang}标题","content":"翻译后的{target_lang}内容"}',
      csvPromptTemplate: '将以下CSV格式数据从{source_lang}翻译为{target_lang}。\n\n规则：\n1. 保持CSV结构和表头title,content不变\n2. 翻译title和content字段的值\n3. 字段值必须使用双引号包裹，字段内双引号用两个双引号转义\n4. 保持Markdown格式\n5. 只返回CSV数据，不添加解释或markdown代码块标记\n\n输入CSV数据：\n{csv_data}\n\n请返回翻译后的CSV数据：\ntitle,content\n"翻译后的{target_lang}标题","翻译后的{target_lang}内容"',
      summaryToonPromptTemplate: '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 保持TOON格式结构不变（2个空格缩进）\n5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请返回TOON格式的摘要，格式如下：\n{toon_example}',
      summaryJsonPromptTemplate: '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 请直接返回JSON格式的摘要，不要添加任何markdown代码块标记、前缀或说明\n5. JSON格式示例：{json_example}\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回JSON格式的摘要：\n{json_example}',
      summaryCsvPromptTemplate: '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 返回CSV格式，表头固定为lang,summary\n5. 字段值必须使用双引号包裹，字段内双引号用两个双引号转义\n6. 只返回CSV数据，不添加解释或markdown代码块标记\n7. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回CSV格式的摘要：\n{csv_example}',
      testSummaryLoading: false,
      testImageLoading: false,
      testImageDialogVisible: false,
      testSummaryDialogVisible: false,
      testTextrankDialogVisible: false,
      testTextrankLoading: false,
      testGlobalAiLoading: false,
      testGlobalAiError: null, // 全局AI测试连接错误信息
      hasArticles: false, // 是否存在文章数据
      testImageForm: {
        title: '人工智能的未来发展',
        content: `# 人工智能的未来发展

人工智能（AI）是计算机科学的一个重要分支，致力于创造能够模拟人类智能行为的系统。近年来，随着深度学习、大语言模型等技术的突破，AI迎来了前所未有的发展机遇。

## 技术突破

深度学习通过多层神经网络从海量数据中自动学习特征表示，在图像识别、自然语言处理、语音识别等领域取得了革命性进展。Transformer架构的出现更是推动了大规模预训练模型的繁荣。

## 应用场景

AI技术已广泛应用于自动驾驶、医疗诊断、智能助手、内容创作等多个领域，正在深刻改变人类的生活和工作方式。`,
        imageUrl: '',
        prompt: '',
        durationMs: null,
        form: '',
        provider: '',
        error: null
      },
      testSummaryForm: {
        content: `# Vue.js入门指南

Vue.js是一个用于构建用户界面的渐进式JavaScript框架。与其它大型框架不同的是，Vue被设计为可以自底向上逐层应用。

## 核心特性

Vue.js的核心库只关注视图层，不仅易于上手，还便于与第三方库或既有项目整合。另一方面，当与现代化的工具链以及各种支持类库结合使用时，Vue也完全能够为复杂的单页应用提供驱动。

## 响应式数据绑定

Vue.js具有响应式数据绑定和组件化的特性，这使得开发者可以轻松构建动态的Web应用程序。通过数据绑定，开发者可以轻松地将数据与视图同步，无需手动操作DOM。`,
        summary: '',
        summaries: null,  // 多语言摘要对象
        processingTime: null,
        method: null,
        toonTokens: null,  // TOON格式消耗的token数
        tokenSavedPercent: null,  // TOON格式节省的token百分比
        error: null
      },
      testTextrankForm: {
        sourceContent: `# Vue.js入门指南

Vue.js是一个用于构建用户界面的渐进式JavaScript框架。与其它大型框架不同的是，Vue被设计为可以自底向上逐层应用。

## 核心特性

Vue.js的核心库只关注视图层，不仅易于上手，还便于与第三方库或既有项目整合。另一方面，当与现代化的工具链以及各种支持类库结合使用时，Vue也完全能够为复杂的单页应用提供驱动。

## 响应式数据绑定

Vue.js具有响应式数据绑定和组件化的特性，这使得开发者可以轻松构建动态的Web应用程序。通过数据绑定，开发者可以轻松地将数据与视图同步，无需手动操作DOM。`,
        targetContent: `# Vue.js Getting Started Guide

Vue.js is a progressive JavaScript framework for building user interfaces. Unlike other monolithic frameworks, Vue is designed from the ground up to be incrementally adoptable.

## Core Features

The core library focuses on the view layer only, making it easy to pick up and integrate with other libraries or existing projects. On the other hand, Vue is also perfectly capable of powering sophisticated Single-Page Applications when used in combination with modern tooling and supporting libraries.

## Reactive Data Binding

Vue.js features reactive data binding and a component-based architecture, enabling developers to easily build dynamic web applications. Through data binding, developers can easily synchronize data with views without manual DOM manipulation.`,
        summaries: null,
        processingTime: null,
        error: null
      }
    };
  },
  created() {
    // 预加载语言映射（包含后台管理用的中文映射）
    preloadLanguageMapping(true);
    this.getApiConfig();
    this.checkArticlesExist();
  },
  computed: {
    needsApiKey() {
      // 所有大模型类型都可能需要API密钥
      return true;
    },
    currentPromptTemplate() {
      if (this.promptDialogFeature === 'summary') {
        if (this.promptDialogType === 'csv') return this.summaryCsvPromptTemplate;
        return this.promptDialogType === 'toon' ? this.summaryToonPromptTemplate : this.summaryJsonPromptTemplate;
      }
      if (this.promptDialogType === 'csv') return this.csvPromptTemplate;
      return this.promptDialogType === 'toon' ? this.toonPromptTemplate : this.jsonPromptTemplate;
    },
    promptDialogFormatLabel() {
      return this.getDataFormatLabel(this.promptDialogType);
    },
    promptDialogTitle() {
      return `${this.promptDialogFeature === 'summary' ? '摘要' : '翻译'} ${this.promptDialogFormatLabel}格式提示词`;
    },
    imageResolutionPresets() {
      const ratio = this.apiConfig.imageSize || '16:9';
      const presets = {
        '1:1': ['1024x1024', '1328x1328', '2048x2048'],
        '16:9': ['1280x720', '1536x864', '1920x1080', '2560x1440', '3840x2160'],
        '9:16': ['720x1280', '864x1536', '1080x1920', '1440x2560', '2160x3840'],
        '4:3': ['1024x768', '1280x960', '1600x1200'],
        '3:4': ['768x1024', '960x1280', '1200x1600']
      };
      return presets[ratio] || presets['16:9'];
    }
  },
  methods: {
    sanitizeMaxTokensField(field, value) {
      const normalized = String(value == null ? '' : value).replace(/[^\d]/g, '');
      if (value !== normalized) {
        this.apiConfig[field] = normalized;
      }
    },

    normalizeMaxTokensField(field) {
      this.apiConfig[field] = this.toPositiveInteger(this.apiConfig[field], 1000);
    },

    toPositiveInteger(value, fallback) {
      const parsed = parseInt(value, 10);
      return Number.isNaN(parsed) || parsed <= 0 ? fallback : parsed;
    },

    getApiProviderName() {
      return this.getApiProviderMeta().label;
    },

    getApiProviderMeta(provider = this.apiConfig.provider) {
      const metas = {
        baidu: {
          label: '百度翻译',
          description: '传统百度翻译 API，配置 APP ID 和密钥。',
          help: ['APP ID 和密钥来自百度翻译开放平台', '接口固定为百度通用翻译 API，无需填写大模型地址'],
          fields: {
            appId: { label: 'APP ID', placeholder: '请输入百度翻译 APP ID', required: true },
            appSecret: { label: '密钥', placeholder: '请输入百度翻译密钥', required: true, secret: true }
          }
        },
        youdao: {
          label: '有道云翻译',
          description: '传统有道智云文本翻译 API，配置 AppKey 和 AppSecret。',
          help: ['应用ID 使用有道智云控制台的 AppKey', '应用密钥使用 AppSecret', '接口固定为有道官方文本翻译接口，无需填写 /v1 或大模型地址'],
          fields: {
            appId: { label: '应用ID / AppKey', placeholder: '请输入有道云应用ID / AppKey', required: true },
            appSecret: { label: '应用密钥 / AppSecret', placeholder: '请输入有道云应用密钥 / AppSecret', required: true, secret: true }
          }
        },
        tencent: {
          label: '腾讯云 TMT',
          description: '腾讯云机器翻译 TMT，使用 SecretId / SecretKey 和 TC3 签名。',
          help: ['SecretId 和 SecretKey 来自腾讯云访问管理', 'Region 默认 ap-guangzhou，可按资源地域修改', 'ProjectId 可留空，默认 0'],
          fields: {
            appId: { label: 'SecretId', placeholder: '请输入腾讯云 SecretId', required: true },
            appSecret: { label: 'SecretKey', placeholder: '请输入腾讯云 SecretKey', required: true, secret: true },
            providerRegion: { label: 'Region', placeholder: 'ap-guangzhou', required: true },
            providerProjectId: { label: 'ProjectId', placeholder: '默认 0' }
          }
        },
        aliyun: {
          label: '阿里云机器翻译',
          description: '阿里云机器翻译 TranslateGeneral，使用 AccessKeyId / AccessKeySecret。',
          help: ['Endpoint 可留空，默认按 Region 生成 mt.{region}.aliyuncs.com', 'Scene 默认 general', '密钥留空保存时只在当前服务商不变时保留旧值'],
          fields: {
            appId: { label: 'AccessKey ID', placeholder: '请输入阿里云 AccessKey ID', required: true },
            appSecret: { label: 'AccessKey Secret', placeholder: '请输入阿里云 AccessKey Secret', required: true, secret: true },
            providerRegion: { label: 'Region', placeholder: 'cn-hangzhou', required: true },
            customUrl: { label: 'Endpoint', placeholder: 'mt.cn-hangzhou.aliyuncs.com' },
            providerScene: { label: 'Scene', placeholder: 'general' }
          }
        },
        volcengine: {
          label: '火山引擎机器翻译',
          description: '火山引擎机器翻译 OpenAPI，使用 AK/SK 签名。',
          help: ['Access Key ID 和 Secret Key 来自火山引擎访问控制', 'Region 默认 cn-north-1', '接口地址固定为火山翻译 OpenAPI'],
          fields: {
            appId: { label: 'Access Key ID', placeholder: '请输入火山 Access Key ID', required: true },
            appSecret: { label: 'Secret Key', placeholder: '请输入火山 Secret Key', required: true, secret: true },
            providerRegion: { label: 'Region', placeholder: 'cn-north-1', required: true }
          }
        },
        huawei: {
          label: '华为云 NLP',
          description: '华为云 NLP 文本翻译，支持 Token 或 AK/SK 鉴权。',
          help: ['Endpoint 需要填写华为云 NLP 服务地址', 'Project ID 为华为云项目 ID', 'Token 鉴权填写 X-Auth-Token，AK/SK 鉴权填写 Access Key ID 和 Secret'],
          fields: {
            customUrl: { label: 'Endpoint', placeholder: 'https://nlp-ext.cn-north-4.myhuaweicloud.com', required: true },
            providerProjectId: { label: 'Project ID', placeholder: '请输入华为云 Project ID', required: true },
            providerAuthType: { label: '鉴权方式', required: true },
            customApiKey: { label: 'Token', placeholder: '请输入 X-Auth-Token', required: true, secret: true },
            appId: { label: 'Access Key ID', placeholder: '请输入华为云 AK', required: true },
            appSecret: { label: 'Secret Access Key', placeholder: '请输入华为云 SK', required: true, secret: true }
          }
        },
        google: {
          label: 'Google Cloud Translation',
          description: 'Google Cloud Translation v2 REST API，使用 API Key。',
          help: ['API Key 来自 Google Cloud 控制台', 'Format 默认 text，可填写 html', 'Model 可选，例如 nmt'],
          fields: {
            customApiKey: { label: 'API Key', placeholder: '请输入 Google Cloud API Key', required: true, secret: true },
            providerFormat: { label: 'Format', placeholder: 'text' },
            providerModel: { label: 'Model', placeholder: '可选，如 nmt' }
          }
        },
        azure_translator: {
          label: 'Azure AI Translator',
          description: 'Azure AI Translator REST API，使用订阅密钥和可选区域。',
          help: ['Endpoint 默认 https://api.cognitive.microsofttranslator.com', '多服务资源通常需要填写 Region', 'Category 可用于自定义翻译模型'],
          fields: {
            customUrl: { label: 'Endpoint', placeholder: 'https://api.cognitive.microsofttranslator.com' },
            customApiKey: { label: 'Subscription Key', placeholder: '请输入 Azure Translator 订阅密钥', required: true, secret: true },
            providerRegion: { label: 'Region', placeholder: '如 eastasia，可选' },
            providerCategory: { label: 'Category', placeholder: '自定义分类，可选' }
          }
        },
        deepl: {
          label: 'DeepL',
          description: 'DeepL Translate API，按 Free/Pro Endpoint 自动选择地址。',
          help: ['Auth Key 来自 DeepL 控制台', 'Endpoint Type 选择 Free 或 Pro', '如需特殊地址可后续用自定义 HTTP 覆盖'],
          fields: {
            customApiKey: { label: 'Auth Key', placeholder: '请输入 DeepL Auth Key', required: true, secret: true },
            endpointType: { label: 'Endpoint Type', required: true }
          }
        },
        aws: {
          label: 'Amazon Translate',
          description: 'Amazon Translate API，使用 AWS SigV4 签名。',
          help: ['Access Key ID 和 Secret Access Key 来自 AWS IAM', 'Region 必填，例如 us-east-1', 'Session Token 仅临时凭证需要填写'],
          fields: {
            appId: { label: 'Access Key ID', placeholder: '请输入 AWS Access Key ID', required: true },
            appSecret: { label: 'Secret Access Key', placeholder: '请输入 AWS Secret Access Key', required: true, secret: true },
            providerRegion: { label: 'Region', placeholder: 'us-east-1', required: true },
            providerSessionToken: { label: 'Session Token', placeholder: '临时凭证可选', secret: true }
          }
        },
        yandex: {
          label: 'Yandex Cloud Translate',
          description: 'Yandex Cloud Translate REST API，使用 API Key 或 IAM Token。',
          help: ['API Key 或 IAM Token 填一个即可', 'Folder ID 可选但推荐填写', '可直接填写 Bearer / Api-Key 前缀，也可只填原始 token'],
          fields: {
            customApiKey: { label: 'API Key / IAM Token', placeholder: '请输入 API Key 或 IAM Token', required: true, secret: true },
            providerProjectId: { label: 'Folder ID', placeholder: '请输入 Yandex Folder ID' }
          }
        },
        custom: {
          label: '自定义HTTP接口',
          description: '自定义传统翻译 HTTP 接口，必须填写完整 URL。',
          help: ['API地址填写完整URL，例如 https://example.com/translate', '请求方式为 POST JSON，请求体包含 text、source_lang、target_lang、from、to', 'API密钥会作为 Authorization Bearer 和 X-API-Key 发送', '密钥2会作为 X-App-Secret 和 X-API-Secret 发送', '系统不会自动补全 /v1、/chat/completions'],
          fields: {
            customUrl: { label: 'API地址', placeholder: 'https://example.com/translate', required: true },
            customApiKey: { label: 'API密钥', placeholder: '请输入自定义HTTP接口密钥', secret: true },
            appSecret: { label: '密钥2(可选)', placeholder: '某些API需要第二个密钥参数', secret: true }
          }
        }
      };
      return metas[provider] || metas.baidu;
    },

    getApiProviderDescription() {
      return this.getApiProviderMeta().description;
    },

    getApiProviderHelp() {
      return this.getApiProviderMeta().help || [];
    },

    getApiFieldMeta(field) {
      return this.getApiProviderMeta().fields[field] || {};
    },

    isApiFieldVisible(field) {
      if (this.apiConfig.provider === 'huawei') {
        if (field === 'customApiKey') {
          return this.apiConfig.providerAuthType !== 'aksk';
        }
        if (field === 'appId' || field === 'appSecret') {
          return this.apiConfig.providerAuthType === 'aksk';
        }
      }
      return !!this.getApiProviderMeta().fields[field];
    },

    isApiFieldRequired(field) {
      return this.isApiFieldVisible(field) && !!this.getApiFieldMeta(field).required;
    },

    getApiFieldLabel(field) {
      return this.getApiFieldMeta(field).label || field;
    },

    getApiFieldPlaceholder(field) {
      return this.getApiFieldMeta(field).placeholder || '';
    },

    hasExistingApiFieldSecret(field) {
      if (field === 'appSecret') {
        if (this.apiConfig.provider === 'baidu') return this.apiConfig.hasExistingBaiduSecret;
        if (this.apiConfig.provider === 'youdao') return this.apiConfig.hasExistingYoudaoSecret;
        if (this.apiConfig.provider === 'custom') return this.apiConfig.hasExistingCustomSecret;
        return this.apiConfig.hasExistingSecondarySecret;
      }
      if (field === 'customApiKey') {
        if (this.apiConfig.provider === 'custom') return this.apiConfig.hasExistingCustomKey;
        return this.apiConfig.hasExistingPrimarySecret;
      }
      if (field === 'providerSessionToken') {
        return this.apiConfig.hasExistingSessionToken;
      }
      return false;
    },

    onApiProviderChange() {
      this.resetApiProviderFields();
      this.applyApiProviderDefaults(this.apiConfig.provider);
    },

    resetApiProviderFields() {
      Object.assign(this.apiConfig, {
        appId: '',
        appSecret: '',
        customUrl: '',
        customApiKey: '',
        providerRegion: '',
        providerProjectId: '',
        providerScene: '',
        providerFormat: '',
        providerModel: '',
        providerAuthType: 'token',
        endpointType: 'free',
        providerCategory: '',
        providerSessionToken: '',
        hasExistingBaiduSecret: false,
        hasExistingYoudaoSecret: false,
        hasExistingCustomKey: false,
        hasExistingCustomSecret: false,
        hasExistingPrimarySecret: false,
        hasExistingSecondarySecret: false,
        hasExistingSessionToken: false
      });
    },

    applyApiProviderDefaults(provider) {
      const defaults = {
        tencent: { providerRegion: 'ap-guangzhou', providerProjectId: '0' },
        aliyun: { providerRegion: 'cn-hangzhou', providerScene: 'general' },
        volcengine: { providerRegion: 'cn-north-1' },
        huawei: { providerAuthType: 'token' },
        google: { providerFormat: 'text' },
        azure_translator: { customUrl: 'https://api.cognitive.microsofttranslator.com' },
        deepl: { endpointType: 'free' },
        aws: { providerRegion: 'us-east-1' },
        yandex: { providerFormat: 'PLAIN_TEXT' }
      };
      Object.assign(this.apiConfig, defaults[provider] || {});
    },

    setExistingSecretFlagsFromCustomConfig(customConfig) {
      this.apiConfig.hasExistingPrimarySecret = !!(
        customConfig.api_key ||
        customConfig.token ||
        customConfig.subscription_key ||
        customConfig.auth_key ||
        customConfig.api_key_or_iam_token
      );
      this.apiConfig.hasExistingSecondarySecret = !!(
        customConfig.app_secret ||
        customConfig.secret_key ||
        customConfig.access_key_secret ||
        customConfig.secret_access_key
      );
      this.apiConfig.hasExistingSessionToken = !!customConfig.session_token;
    },

    getApiFieldValue(field) {
      return this.apiConfig[field];
    },

    validateApiProviderConfig() {
      const fields = Object.keys(this.getApiProviderMeta().fields);
      for (const field of fields) {
        if (!this.isApiFieldRequired(field)) {
          continue;
        }
        const value = this.getApiFieldValue(field);
        const fieldMeta = this.getApiFieldMeta(field);
        const hasExistingSecret = fieldMeta.secret && this.hasExistingApiFieldSecret(field);
        if ((!value || String(value).trim() === '') && !hasExistingSecret) {
          this.$message.warning(`请填写${this.getApiFieldLabel(field)}`);
          return false;
        }
      }
      return true;
    },

    assignIfText(target, key, value) {
      if (value !== undefined && value !== null && String(value).trim() !== '') {
        target[key] = String(value).trim();
      }
    },

    assignSecretIfText(target, key, value) {
      if (value && String(value).trim() !== '') {
        target[key] = String(value).trim();
      }
    },

    getSecretFieldMeta(secretType) {
      const metaMap = {
        llm: {
          input: 'llmApiKey',
          existing: 'hasExistingLlmKey',
          clear: 'clearExistingLlmKey'
        },
        translation: {
          input: 'translationLlmApiKey',
          existing: 'hasExistingTranslationLlmKey',
          clear: 'clearExistingTranslationLlmKey'
        },
        summary: {
          input: 'summaryLlmApiKey',
          existing: 'hasExistingSummaryLlmKey',
          clear: 'clearExistingSummaryLlmKey'
        },
        image: {
          input: 'imageApiKey',
          existing: 'hasExistingImageKey',
          clear: 'clearExistingImageKey'
        },
        imageLlm: {
          input: 'imageLlmApiKey',
          existing: 'hasExistingImageLlmKey',
          clear: 'clearExistingImageLlmKey'
        }
      };
      return metaMap[secretType];
    },

    markSecretForClear(secretType) {
      const meta = this.getSecretFieldMeta(secretType);
      if (!meta) return;

      this.apiConfig[meta.input] = '';
      this.apiConfig[meta.existing] = false;
      this.apiConfig[meta.clear] = true;
      this.$message.info('保存配置后将清除已保存密钥');
    },

    cancelSecretClear(secretType, restoreExisting = false) {
      const meta = this.getSecretFieldMeta(secretType);
      if (!meta || !this.apiConfig[meta.clear]) return;

      this.apiConfig[meta.clear] = false;
      if (restoreExisting && !this.apiConfig[meta.input]) {
        this.apiConfig[meta.existing] = true;
      }
    },

    resetSecretClearFlags() {
      this.apiConfig.clearExistingLlmKey = false;
      this.apiConfig.clearExistingTranslationLlmKey = false;
      this.apiConfig.clearExistingSummaryLlmKey = false;
      this.apiConfig.clearExistingImageKey = false;
      this.apiConfig.clearExistingImageLlmKey = false;
    },

    buildApiProviderConfig() {
      const provider = this.apiConfig.provider;
      if (provider === 'baidu') {
        const baiduConfig = {
          app_id: this.apiConfig.appId
        };
        this.assignSecretIfText(baiduConfig, 'app_secret', this.apiConfig.appSecret);
        return baiduConfig;
      }

      const config = { provider };
      switch (provider) {
        case 'youdao':
          config.app_key = this.apiConfig.appId;
          config.api_key = this.apiConfig.appId;
          this.assignSecretIfText(config, 'app_secret', this.apiConfig.appSecret);
          break;
        case 'tencent':
          config.secret_id = this.apiConfig.appId;
          this.assignSecretIfText(config, 'secret_key', this.apiConfig.appSecret);
          this.assignIfText(config, 'region', this.apiConfig.providerRegion);
          this.assignIfText(config, 'project_id', this.apiConfig.providerProjectId);
          break;
        case 'aliyun':
          config.access_key_id = this.apiConfig.appId;
          this.assignSecretIfText(config, 'access_key_secret', this.apiConfig.appSecret);
          this.assignIfText(config, 'region', this.apiConfig.providerRegion);
          this.assignIfText(config, 'endpoint', this.apiConfig.customUrl);
          this.assignIfText(config, 'scene', this.apiConfig.providerScene);
          break;
        case 'volcengine':
          config.access_key_id = this.apiConfig.appId;
          this.assignSecretIfText(config, 'secret_key', this.apiConfig.appSecret);
          this.assignIfText(config, 'region', this.apiConfig.providerRegion);
          break;
        case 'huawei':
          this.assignIfText(config, 'endpoint', this.apiConfig.customUrl);
          this.assignIfText(config, 'project_id', this.apiConfig.providerProjectId);
          config.auth_type = this.apiConfig.providerAuthType || 'token';
          if (config.auth_type === 'aksk') {
            config.access_key_id = this.apiConfig.appId;
            this.assignSecretIfText(config, 'access_key_secret', this.apiConfig.appSecret);
          } else {
            this.assignSecretIfText(config, 'token', this.apiConfig.customApiKey);
          }
          break;
        case 'google':
          this.assignSecretIfText(config, 'api_key', this.apiConfig.customApiKey);
          this.assignIfText(config, 'format', this.apiConfig.providerFormat);
          this.assignIfText(config, 'model', this.apiConfig.providerModel);
          break;
        case 'azure_translator':
          this.assignIfText(config, 'endpoint', this.apiConfig.customUrl);
          this.assignSecretIfText(config, 'subscription_key', this.apiConfig.customApiKey);
          this.assignIfText(config, 'region', this.apiConfig.providerRegion);
          this.assignIfText(config, 'category', this.apiConfig.providerCategory);
          break;
        case 'deepl':
          this.assignSecretIfText(config, 'auth_key', this.apiConfig.customApiKey);
          this.assignIfText(config, 'endpoint_type', this.apiConfig.endpointType);
          break;
        case 'aws':
          config.access_key_id = this.apiConfig.appId;
          this.assignSecretIfText(config, 'secret_access_key', this.apiConfig.appSecret);
          this.assignIfText(config, 'region', this.apiConfig.providerRegion);
          this.assignSecretIfText(config, 'session_token', this.apiConfig.providerSessionToken);
          break;
        case 'yandex':
          this.assignSecretIfText(config, 'api_key_or_iam_token', this.apiConfig.customApiKey);
          this.assignIfText(config, 'folder_id', this.apiConfig.providerProjectId);
          break;
        case 'custom':
          this.assignIfText(config, 'api_url', this.apiConfig.customUrl);
          this.assignSecretIfText(config, 'api_key', this.apiConfig.customApiKey);
          this.assignSecretIfText(config, 'app_secret', this.apiConfig.appSecret);
          break;
      }
      return config;
    },

    applyLoadedApiProviderConfig(provider, providerConfig) {
      this.apiConfig.provider = provider;
      this.resetApiProviderFields();
      this.apiConfig.provider = provider;
      this.applyApiProviderDefaults(provider);

      if (provider === 'baidu') {
        this.apiConfig.appId = providerConfig.app_id || '';
        this.apiConfig.hasExistingBaiduSecret = !!providerConfig.app_secret;
        this.apiConfig.appSecret = '';
        return;
      }

      this.setExistingSecretFlagsFromCustomConfig(providerConfig);
      switch (provider) {
        case 'youdao':
          this.apiConfig.appId = providerConfig.app_key || providerConfig.api_key || '';
          this.apiConfig.hasExistingYoudaoSecret = !!providerConfig.app_secret;
          break;
        case 'tencent':
          this.apiConfig.appId = providerConfig.secret_id || '';
          this.apiConfig.providerRegion = providerConfig.region || this.apiConfig.providerRegion;
          this.apiConfig.providerProjectId = providerConfig.project_id || this.apiConfig.providerProjectId;
          break;
        case 'aliyun':
          this.apiConfig.appId = providerConfig.access_key_id || '';
          this.apiConfig.providerRegion = providerConfig.region || this.apiConfig.providerRegion;
          this.apiConfig.customUrl = providerConfig.endpoint || '';
          this.apiConfig.providerScene = providerConfig.scene || this.apiConfig.providerScene;
          break;
        case 'volcengine':
          this.apiConfig.appId = providerConfig.access_key_id || '';
          this.apiConfig.providerRegion = providerConfig.region || this.apiConfig.providerRegion;
          break;
        case 'huawei':
          this.apiConfig.customUrl = providerConfig.endpoint || '';
          this.apiConfig.providerProjectId = providerConfig.project_id || '';
          this.apiConfig.providerAuthType = providerConfig.auth_type || 'token';
          this.apiConfig.appId = providerConfig.access_key_id || '';
          break;
        case 'google':
          this.apiConfig.providerFormat = providerConfig.format || this.apiConfig.providerFormat;
          this.apiConfig.providerModel = providerConfig.model || '';
          break;
        case 'azure_translator':
          this.apiConfig.customUrl = providerConfig.endpoint || this.apiConfig.customUrl;
          this.apiConfig.providerRegion = providerConfig.region || '';
          this.apiConfig.providerCategory = providerConfig.category || '';
          break;
        case 'deepl':
          this.apiConfig.endpointType = providerConfig.endpoint_type || this.apiConfig.endpointType;
          break;
        case 'aws':
          this.apiConfig.appId = providerConfig.access_key_id || '';
          this.apiConfig.providerRegion = providerConfig.region || this.apiConfig.providerRegion;
          break;
        case 'yandex':
          this.apiConfig.providerProjectId = providerConfig.folder_id || '';
          break;
        case 'custom':
          this.apiConfig.customUrl = providerConfig.api_url || '';
          this.apiConfig.hasExistingCustomKey = !!providerConfig.api_key;
          this.apiConfig.hasExistingCustomSecret = !!providerConfig.app_secret;
          break;
      }
      this.apiConfig.customApiKey = '';
      this.apiConfig.appSecret = '';
      this.apiConfig.providerSessionToken = '';
    },

    getLlmInterfaceTip(interfaceType) {
      if (interfaceType === 'custom') {
        return '自定义OpenAI兼容接口仍走大模型SDK，API地址填写服务商基础地址即可，通常到 /v1，系统会自动拼接 /chat/completions。';
      }
      if (interfaceType === 'openai_completions') {
        return '当前暂不支持 /v1/completions。';
      }
      return 'API地址填写服务商基础地址即可，通常到 /v1，系统会自动拼接具体接口。';
    },

    formatJsonObject(value) {
      if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length === 0) {
        return '';
      }
      return JSON.stringify(value, null, 2);
    },

    parseJsonObjectField(text, label) {
      if (!text || !String(text).trim()) {
        return { valid: true, value: {} };
      }
      try {
        const parsed = JSON.parse(text);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          return { valid: false, value: {}, message: `${label}必须是 JSON 对象` };
        }
        return { valid: true, value: parsed };
      } catch (error) {
        return { valid: false, value: {}, message: `${label} JSON 格式错误` };
      }
    },

    isOpenRouterConfig(config) {
      const apiUrl = (config && config.api_url ? String(config.api_url) : '').toLowerCase();
      return apiUrl.includes('openrouter.ai');
    },

    isWorldRouterConfig(config) {
      const apiUrl = (config && config.api_url ? String(config.api_url) : '').toLowerCase();
      return apiUrl.includes('worldrouter.ai');
    },

    normalizeCustomInterfaceType(interfaceType) {
      const normalized = String(interfaceType || 'auto').toLowerCase();
      if (['auto', 'openai', 'anthropic', 'custom'].includes(normalized)) {
        return normalized;
      }
      if (['deepseek', 'siliconflow', 'openrouter', 'worldrouter', 'openai_chat', 'openai_compatible', 'chat_completions'].includes(normalized)) {
        return 'openai';
      }
      return 'auto';
    },

    profileFromInterfaceType(interfaceType) {
      const normalized = String(interfaceType || '').toLowerCase();
      const profileMap = {
        deepseek: 'deepseek_official',
        siliconflow: 'siliconflow',
        openrouter: 'openrouter',
        worldrouter: 'worldrouter',
        anthropic: 'anthropic',
        openai: 'openai'
      };
      return profileMap[normalized] || '';
    },

    // API配置相关方法
    async getApiConfig() {
      try {
        this.loading = true;
        this.resetSecretClearFlags();
        
        // 先检查是否有文章（决定是否允许修改源语言）
        await this.checkArticlesExist();
        
        const res = await this.$http.get(this.$constant.baseURL + '/webInfo/ai/config/articleAi/get', {}, true);
        
        
        if (res && res.code === 200 && res.data) {
          // 设置当前翻译类型（Java驼峰格式）
          if (res.data.translationType === 'none') {
            this.apiConfig.mode = 'none';
          } else if (res.data.translationType === 'dedicated_llm') {
            this.apiConfig.mode = 'dedicated_llm';
          } else if (res.data.translationType === 'llm') {
            this.apiConfig.mode = 'llm';
          } else {
            this.apiConfig.mode = 'api';
          }
          
          
          // 处理传统 API 翻译配置
          if (res.data.translationType === 'baidu' && res.data.baiduConfig) {
            const baiduConfig = typeof res.data.baiduConfig === 'string' 
              ? JSON.parse(res.data.baiduConfig) 
              : res.data.baiduConfig;
            this.applyLoadedApiProviderConfig('baidu', baiduConfig);
          }
          
          if (res.data.translationType !== 'baidu' && res.data.customConfig) {
            const customConfig = typeof res.data.customConfig === 'string' 
              ? JSON.parse(res.data.customConfig) 
              : res.data.customConfig;
            const provider = customConfig.provider || res.data.translationType;
            if (provider && provider !== 'baidu') {
              this.applyLoadedApiProviderConfig(provider, customConfig);
            }
          }
          
          // 处理LLM配置
          if (res.data.llmConfig) {
            // 如果是JSON字符串，先解析
            const llmConfig = typeof res.data.llmConfig === 'string' 
              ? JSON.parse(res.data.llmConfig) 
              : res.data.llmConfig;
            
            this.apiConfig.llmModel = llmConfig.model || '';
            this.apiConfig.llmUrl = llmConfig.api_url || '';
            this.apiConfig.hasExistingLlmKey = !!(llmConfig.api_key && llmConfig.api_key !== '' && llmConfig.api_key !== 'null');
            this.apiConfig.llmApiKey = ''; // 不显示已有密钥内容
            this.apiConfig.llmPrompt = llmConfig.prompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}';
            this.apiConfig.llmInterfaceType = llmConfig.interface_type || 'auto';  // 读取接口类型
            this.apiConfig.llmTimeout = llmConfig.timeout || 30;  // 读取超时时间
            this.apiConfig.llmMaxTokens = llmConfig.max_tokens || 1000;  // 读取max_tokens
            this.apiConfig.llmTemperature = llmConfig.temperature || 0.7;
            this.apiConfig.llmTopP = llmConfig.top_p || 1.0;
            this.apiConfig.llmFrequencyPenalty = llmConfig.frequency_penalty || 0;
            this.apiConfig.llmPresencePenalty = llmConfig.presence_penalty || 0;
            this.apiConfig.llmReasoningEffort = llmConfig.reasoning_effort || '';
            this.apiConfig.llmThinkingProfile = llmConfig.thinking_profile || 'auto';
            this.apiConfig.llmThinkingExtraBodyText = this.formatJsonObject(llmConfig.thinking_extra_body);
            
            // 优先使用original_type（新版本），如果没有则从interface_type推断（兼容旧数据）
            if (llmConfig.original_type) {
              // 新版本：直接使用original_type
              this.apiConfig.llmType = llmConfig.original_type;
              if (this.apiConfig.llmType === 'custom') {
                const profileFromInterface = this.profileFromInterfaceType(llmConfig.interface_type);
                this.apiConfig.llmInterfaceType = this.normalizeCustomInterfaceType(llmConfig.interface_type);
                if ((!llmConfig.thinking_profile || llmConfig.thinking_profile === 'auto') && profileFromInterface) {
                  this.apiConfig.llmThinkingProfile = profileFromInterface;
                }
              }
            } else {
              // 兼容旧数据：从interface_type推断
              const interfaceType = llmConfig.interface_type;
              if (interfaceType && interfaceType !== 'auto') {
                this.apiConfig.llmType = interfaceType;
              } else {
                // 兼容旧数据或auto模式：根据模型类型推断LLM类型
              if (this.isOpenRouterConfig(llmConfig)) {
                this.apiConfig.llmType = 'openrouter';
              } else if (this.isWorldRouterConfig(llmConfig)) {
                this.apiConfig.llmType = 'worldrouter';
              } else if (llmConfig.model) {
                const model = llmConfig.model.toLowerCase();
                if (model.includes('gpt') || model.includes('openai')) {
                  this.apiConfig.llmType = 'openai';
                } else if (model.includes('claude') || model.includes('anthropic')) {
                  this.apiConfig.llmType = 'anthropic';
                } else if (model.includes('deepseek-v4') || model.includes('deepseek-chat') || model.includes('deepseek-coder') || model.includes('deepseek-reasoner')) {
                  // DeepSeek 官方模型
                  this.apiConfig.llmType = 'deepseek';
                } else if (model.includes('qwen/') || model.includes('deepseek-ai/') || 
                           model.includes('thudm/') || model.includes('meta-llama/') ||
                           model.includes('qwq') || model.includes('glm-')) {
                  // 硅基流动的模型通常以组织名/模型名格式命名
                  this.apiConfig.llmType = 'siliconflow';
                } else if (model.includes('azure')) {
                  this.apiConfig.llmType = 'azure';
                } else {
                  this.apiConfig.llmType = 'custom';
                }
              }
              }
            }
            
            // 保存原始LLM配置，用于智能恢复
            this.savedLlmConfig = {
              type: this.apiConfig.llmType,
              model: this.apiConfig.llmModel,
              url: this.apiConfig.llmUrl,
              interfaceType: this.apiConfig.llmInterfaceType,  // 自定义类型需要
              reasoningEffort: this.apiConfig.llmReasoningEffort,
              thinkingProfile: this.apiConfig.llmThinkingProfile,
              thinkingExtraBodyText: this.apiConfig.llmThinkingExtraBodyText
            };
          }
          
          // 处理翻译独立AI配置
          if (res.data.translationLlmConfig) {
            // 如果是JSON字符串，先解析
            const translationLlm = typeof res.data.translationLlmConfig === 'string' 
              ? JSON.parse(res.data.translationLlmConfig) 
              : res.data.translationLlmConfig;
            this.apiConfig.translationLlmModel = translationLlm.model || '';
            this.apiConfig.translationLlmUrl = translationLlm.api_url || '';
            this.apiConfig.translationLlmInterfaceType = translationLlm.interface_type || 'auto';
            this.apiConfig.translationLlmTimeout = translationLlm.timeout || 30;
            this.apiConfig.translationLlmMaxTokens = translationLlm.max_tokens || 1000;
            this.apiConfig.translationLlmTemperature = translationLlm.temperature || 0.7;
            this.apiConfig.translationLlmTopP = translationLlm.top_p || 1.0;
            this.apiConfig.translationLlmFrequencyPenalty = translationLlm.frequency_penalty || 0;
            this.apiConfig.translationLlmPresencePenalty = translationLlm.presence_penalty || 0;
            this.apiConfig.translationLlmReasoningEffort = translationLlm.reasoning_effort || '';
            this.apiConfig.translationLlmThinkingProfile = translationLlm.thinking_profile || 'auto';
            this.apiConfig.translationLlmThinkingExtraBodyText = this.formatJsonObject(translationLlm.thinking_extra_body);
            this.apiConfig.hasExistingTranslationLlmKey = !!(translationLlm.api_key && translationLlm.api_key !== '' && translationLlm.api_key !== 'null');
            this.apiConfig.translationLlmApiKey = ''; // 不显示已有密钥内容
            
            // 如果是 dedicated_llm 模式，翻译提示词使用翻译独立AI的prompt
            if (this.apiConfig.mode === 'dedicated_llm') {
              this.apiConfig.llmPrompt = translationLlm.prompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}';
            }
            
            // 优先使用original_type（新版本），如果没有则从 interface_type推断（兼容旧数据）
            if (translationLlm.original_type) {
              // 新版本：直接使用original_type
              this.apiConfig.translationLlmType = translationLlm.original_type;
              if (this.apiConfig.translationLlmType === 'custom') {
                const profileFromInterface = this.profileFromInterfaceType(translationLlm.interface_type);
                this.apiConfig.translationLlmInterfaceType = this.normalizeCustomInterfaceType(translationLlm.interface_type);
                if ((!translationLlm.thinking_profile || translationLlm.thinking_profile === 'auto') && profileFromInterface) {
                  this.apiConfig.translationLlmThinkingProfile = profileFromInterface;
                }
              }
            } else {
              // 兼容旧数据：从 interface_type推断
              const translationInterfaceType = translationLlm.interface_type;
              if (translationInterfaceType && translationInterfaceType !== 'auto') {
                this.apiConfig.translationLlmType = translationInterfaceType;
              } else {
                // 兼容旧数据或auto模式：根据模型类型推断LLM类型
              if (this.isOpenRouterConfig(translationLlm)) {
                this.apiConfig.translationLlmType = 'openrouter';
              } else if (this.isWorldRouterConfig(translationLlm)) {
                this.apiConfig.translationLlmType = 'worldrouter';
              } else if (translationLlm.model) {
                const model = translationLlm.model.toLowerCase();
                if (model.includes('gpt') || model.includes('openai')) {
                  this.apiConfig.translationLlmType = 'openai';
                } else if (model.includes('claude') || model.includes('anthropic')) {
                  this.apiConfig.translationLlmType = 'anthropic';
                } else if (model.includes('deepseek-v4') || model.includes('deepseek-chat') || model.includes('deepseek-coder') || model.includes('deepseek-reasoner')) {
                  this.apiConfig.translationLlmType = 'deepseek';
                } else if (model.includes('qwen/') || model.includes('deepseek-ai/') || 
                           model.includes('thudm/') || model.includes('meta-llama/') ||
                           model.includes('qwq') || model.includes('glm-')) {
                  this.apiConfig.translationLlmType = 'siliconflow';
                } else if (model.includes('azure')) {
                  this.apiConfig.translationLlmType = 'azure';
                } else {
                  this.apiConfig.translationLlmType = 'custom';
                }
              }
              }
            }
            
            // 保存原姻翻译独立AI配置，用于智能恢复
            this.savedTranslationLlmConfig = {
              type: this.apiConfig.translationLlmType,
              model: this.apiConfig.translationLlmModel,
              url: this.apiConfig.translationLlmUrl,
              interfaceType: this.apiConfig.translationLlmInterfaceType,  // 自定义类型需要
              reasoningEffort: this.apiConfig.translationLlmReasoningEffort,
              thinkingProfile: this.apiConfig.translationLlmThinkingProfile,
              thinkingExtraBodyText: this.apiConfig.translationLlmThinkingExtraBodyText
            };
          }
          
          // 处理默认语言配置（Java驼峰格式）
          if (res.data.defaultSourceLang) {
            this.apiConfig.defaultSourceLang = res.data.defaultSourceLang;
          }
          if (res.data.defaultTargetLang) {
            this.apiConfig.defaultTargetLang = res.data.defaultTargetLang;
          }
          
          // 处理摘要配置
          if (res.data.summaryConfig) {
            // 如果是JSON字符串，先解析
            const summaryConfig = typeof res.data.summaryConfig === 'string' 
              ? JSON.parse(res.data.summaryConfig) 
              : res.data.summaryConfig;
            
            this.apiConfig.summaryMode = summaryConfig.summaryMode || 'disabled';
            this.apiConfig.summaryStyle = summaryConfig.style || 'concise';
            this.apiConfig.summaryMaxLength = summaryConfig.max_length || 150;
            this.apiConfig.summaryPrompt = summaryConfig.prompt || '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 请直接返回JSON格式的摘要，不要添加任何markdown代码块标记、前缀或说明\n5. JSON格式示例：{json_example}\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回JSON格式的摘要：\n{json_example}';
            
            // 处理独立AI配置
            if (summaryConfig.dedicated_llm) {
              const dedicatedLlm = summaryConfig.dedicated_llm;
              this.apiConfig.summaryLlmModel = dedicatedLlm.model || '';
              this.apiConfig.summaryLlmUrl = dedicatedLlm.api_url || '';
              this.apiConfig.summaryLlmInterfaceType = dedicatedLlm.interface_type || 'auto';
              this.apiConfig.summaryLlmTimeout = dedicatedLlm.timeout || 30;
              this.apiConfig.summaryLlmMaxTokens = dedicatedLlm.max_tokens || 1000;
              this.apiConfig.summaryLlmTemperature = dedicatedLlm.temperature || 0.7;
              this.apiConfig.summaryLlmTopP = dedicatedLlm.top_p || 1.0;
              this.apiConfig.summaryLlmFrequencyPenalty = dedicatedLlm.frequency_penalty || 0;
              this.apiConfig.summaryLlmPresencePenalty = dedicatedLlm.presence_penalty || 0;
              this.apiConfig.summaryLlmReasoningEffort = dedicatedLlm.reasoning_effort || '';
              this.apiConfig.summaryLlmThinkingProfile = dedicatedLlm.thinking_profile || 'auto';
              this.apiConfig.summaryLlmThinkingExtraBodyText = this.formatJsonObject(dedicatedLlm.thinking_extra_body);
              this.apiConfig.hasExistingSummaryLlmKey = !!(dedicatedLlm.api_key && dedicatedLlm.api_key !== '' && dedicatedLlm.api_key !== 'null');
              this.apiConfig.summaryLlmApiKey = ''; // 不显示已有密钥内容
              
              // 优先使用original_type（新版本），如果没有则从interface_type推断（兼容旧数据）
              if (dedicatedLlm.original_type) {
                // 新版本：直接使用original_type
                this.apiConfig.summaryLlmType = dedicatedLlm.original_type;
                if (this.apiConfig.summaryLlmType === 'custom') {
                  const profileFromInterface = this.profileFromInterfaceType(dedicatedLlm.interface_type);
                  this.apiConfig.summaryLlmInterfaceType = this.normalizeCustomInterfaceType(dedicatedLlm.interface_type);
                  if ((!dedicatedLlm.thinking_profile || dedicatedLlm.thinking_profile === 'auto') && profileFromInterface) {
                    this.apiConfig.summaryLlmThinkingProfile = profileFromInterface;
                  }
                }
              } else {
                // 兼容旧数据：从interface_type推断
                const summaryInterfaceType = dedicatedLlm.interface_type;
                if (summaryInterfaceType && summaryInterfaceType !== 'auto') {
                  this.apiConfig.summaryLlmType = summaryInterfaceType;
                } else {
                  // 兼容旧数据或auto模式：根据模型类型推断LLM类型
                if (this.isOpenRouterConfig(dedicatedLlm)) {
                  this.apiConfig.summaryLlmType = 'openrouter';
                } else if (this.isWorldRouterConfig(dedicatedLlm)) {
                  this.apiConfig.summaryLlmType = 'worldrouter';
                } else if (dedicatedLlm.model) {
                  const model = dedicatedLlm.model.toLowerCase();
                  if (model.includes('gpt') || model.includes('openai')) {
                    this.apiConfig.summaryLlmType = 'openai';
                  } else if (model.includes('claude') || model.includes('anthropic')) {
                    this.apiConfig.summaryLlmType = 'anthropic';
                  } else if (model.includes('deepseek-v4') || model.includes('deepseek-chat') || model.includes('deepseek-coder') || model.includes('deepseek-reasoner')) {
                    this.apiConfig.summaryLlmType = 'deepseek';
                  } else if (model.includes('qwen/') || model.includes('deepseek-ai/') || 
                             model.includes('thudm/') || model.includes('meta-llama/') ||
                             model.includes('qwq') || model.includes('glm-')) {
                    this.apiConfig.summaryLlmType = 'siliconflow';
                  } else if (model.includes('azure')) {
                    this.apiConfig.summaryLlmType = 'azure';
                  } else {
                    this.apiConfig.summaryLlmType = 'custom';
                  }
                }
                }
              }
            }
            
            // 保存原始摘要独立AI配置，用于智能恢复
            if (res.data.summaryConfig.dedicated_llm) {
              this.savedSummaryLlmConfig = {
                type: this.apiConfig.summaryLlmType,
                model: this.apiConfig.summaryLlmModel,
                url: this.apiConfig.summaryLlmUrl,
                interfaceType: this.apiConfig.summaryLlmInterfaceType,  // 自定义类型需要
                reasoningEffort: this.apiConfig.summaryLlmReasoningEffort,
                thinkingProfile: this.apiConfig.summaryLlmThinkingProfile,
                thinkingExtraBodyText: this.apiConfig.summaryLlmThinkingExtraBodyText
              };
            }
          } else {
            // 如果没有摘要配置，使用默认值
            this.apiConfig.summaryMode = 'disabled';
            this.apiConfig.summaryStyle = 'concise';
            this.apiConfig.summaryMaxLength = 150;
            this.apiConfig.summaryPrompt = '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 请直接返回JSON格式的摘要，不要添加任何markdown代码块标记、前缀或说明\n5. JSON格式示例：{json_example}\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回JSON格式的摘要：\n{json_example}';
          }

          // 处理生图配置
          if (res.data.imageConfig) {
            const imageConfig = typeof res.data.imageConfig === 'string'
              ? JSON.parse(res.data.imageConfig)
              : res.data.imageConfig;
            this.apiConfig.imageMode = imageConfig.imageMode || 'disabled';
            this.apiConfig.imageProvider = imageConfig.provider || 'siliconflow';
            this.apiConfig.imageModel = imageConfig.model || '';
            this.apiConfig.imageUrl = imageConfig.api_url || '';
            this.apiConfig.imageSize = imageConfig.size || '1:1';
            this.apiConfig.imageResolution = imageConfig.resolution || '1536x864';
            this.apiConfig.imageQuality = imageConfig.quality || 'auto';
            // 真实感封面模板回显（后端已将旧值 none 归一化为 object）
            this.apiConfig.coverTemplate = imageConfig.cover_template || 'object';
            // 自定义模板的 LLM 系统提示词回显
            this.apiConfig.customRefinePrompt = imageConfig.custom_refine_prompt || '';
            this.apiConfig.imageTimeout = imageConfig.timeout || 120;
            this.apiConfig.hasExistingImageKey = !!(imageConfig.api_key && imageConfig.api_key !== '' && imageConfig.api_key !== 'null');
            this.apiConfig.imageApiKey = '';
            // 独立AI配置
            if (imageConfig.dedicated_llm) {
              const dil = imageConfig.dedicated_llm;
              this.apiConfig.imageLlmModel = dil.model || '';
              this.apiConfig.imageLlmUrl = dil.api_url || '';
              this.apiConfig.imageLlmTimeout = dil.timeout || 30;
              this.apiConfig.imageLlmInterfaceType = dil.interface_type || 'auto';
              this.apiConfig.hasExistingImageLlmKey = !!(dil.api_key && dil.api_key !== '' && dil.api_key !== 'null');
              this.apiConfig.imageLlmApiKey = '';
              if (dil.original_type) {
                this.apiConfig.imageLlmType = dil.original_type;
              } else if (dil.interface_type && dil.interface_type !== 'auto') {
                this.apiConfig.imageLlmType = dil.interface_type;
              } else {
                this.apiConfig.imageLlmType = 'openai';
              }
            }
          }

          // 自动填充空的API地址
          if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
            this.onLlmTypeChange(this.apiConfig.llmType);
          }
          if (!this.apiConfig.translationLlmUrl || this.apiConfig.translationLlmUrl.trim() === '') {
            this.onTranslationLlmTypeChange(this.apiConfig.translationLlmType);
          }
          if (!this.apiConfig.summaryLlmUrl || this.apiConfig.summaryLlmUrl.trim() === '') {
            this.onSummaryLlmTypeChange(this.apiConfig.summaryLlmType);
          }
        } else {
          this.$message.error('获取配置失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('获取API配置失败:', error);
        this.$message.error('获取配置失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },
    async saveApiConfig() {
      try {
        this.loading = true;
        // 保存前校验分辨率（仅非 gemini 需要校验）
        if (this.apiConfig.imageProvider !== 'gemini' && !this.validateImageResolution()) {
          this.$message.error(this.imageResolutionError || '分辨率格式或比例不正确');
          return;
        }
        const llmThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.llmThinkingExtraBodyText, '全局模型自定义请求参数');
        const translationThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.translationLlmThinkingExtraBodyText, '翻译独立模型自定义请求参数');
        const summaryThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.summaryLlmThinkingExtraBodyText, '摘要独立模型自定义请求参数');
        if (!llmThinkingExtraBody.valid || !translationThinkingExtraBody.valid || !summaryThinkingExtraBody.valid) {
          this.$message.error((!llmThinkingExtraBody.valid && llmThinkingExtraBody.message)
            || (!translationThinkingExtraBody.valid && translationThinkingExtraBody.message)
            || summaryThinkingExtraBody.message);
          return;
        }
        
        // 构建配置对象（Java驼峰格式）
        const config = {
          configType: 'article_ai',
          configName: 'default'
        };
        
        // 全局AI模型配置（始终保存）
        const llmConfigObj = {
          model: this.apiConfig.llmModel,
          api_url: this.apiConfig.llmUrl,
          prompt: this.apiConfig.llmPrompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}',
          // 保存原始类型，用于区分custom和其他类型
          original_type: this.apiConfig.llmType,
          // 如果是custom，使用llmInterfaceType；否则使用llmType
          interface_type: this.apiConfig.llmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.llmInterfaceType) : this.apiConfig.llmType,
          timeout: this.apiConfig.llmTimeout || 30,
          max_tokens: this.toPositiveInteger(this.apiConfig.llmMaxTokens, 1000),
          temperature: this.apiConfig.llmTemperature || 0.7,
          top_p: this.apiConfig.llmTopP || 1.0,
          frequency_penalty: this.apiConfig.llmFrequencyPenalty || 0,
          presence_penalty: this.apiConfig.llmPresencePenalty || 0,
          thinking_profile: this.apiConfig.llmThinkingProfile || 'auto',
          thinking_extra_body: llmThinkingExtraBody.value
        };
        if (this.apiConfig.llmReasoningEffort) {
          llmConfigObj.reasoning_effort = this.apiConfig.llmReasoningEffort;
        }
        // 留空代表保留旧密钥；点击“清除已保存密钥”才发送空值清除。
        if (this.apiConfig.clearExistingLlmKey) {
          llmConfigObj.api_key = '';
        } else if (this.apiConfig.llmApiKey && this.apiConfig.llmApiKey.trim() !== '') {
          llmConfigObj.api_key = this.apiConfig.llmApiKey;
        }
        // 序列化为JSON字符串
        config.llmConfig = JSON.stringify(llmConfigObj);
        
        // 根据翻译模式设置不同的配置
        if (this.apiConfig.mode === 'none') {
          // 不翻译模式
          config.translationType = 'none';
        } else if (this.apiConfig.mode === 'api') {
          if (!this.validateApiProviderConfig()) {
            return;
          }
          config.translationType = this.apiConfig.provider;
          const providerConfig = this.buildApiProviderConfig();
          if (this.apiConfig.provider === 'baidu') {
            config.baiduConfig = JSON.stringify(providerConfig);
          } else {
            config.customConfig = JSON.stringify(providerConfig);
          }
        } else if (this.apiConfig.mode === 'llm') {
          config.translationType = 'llm';
        } else if (this.apiConfig.mode === 'dedicated_llm') {
          config.translationType = 'dedicated_llm';
          // 保存翻译独立AI配置
          const translationLlmConfigObj = {
            model: this.apiConfig.translationLlmModel,
            api_url: this.apiConfig.translationLlmUrl,
            prompt: this.apiConfig.llmPrompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}',
            // 保存原始类型，用于区分custom和其他类型
            original_type: this.apiConfig.translationLlmType,
            // 如果是custom，使用translationLlmInterfaceType；否则使用translationLlmType
            interface_type: this.apiConfig.translationLlmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.translationLlmInterfaceType) : this.apiConfig.translationLlmType,
            timeout: this.apiConfig.translationLlmTimeout || 30,
            max_tokens: this.toPositiveInteger(this.apiConfig.translationLlmMaxTokens, 1000),
            temperature: this.apiConfig.translationLlmTemperature || 0.7,
            top_p: this.apiConfig.translationLlmTopP || 1.0,
            frequency_penalty: this.apiConfig.translationLlmFrequencyPenalty || 0,
            presence_penalty: this.apiConfig.translationLlmPresencePenalty || 0,
            thinking_profile: this.apiConfig.translationLlmThinkingProfile || 'auto',
            thinking_extra_body: translationThinkingExtraBody.value
          };
          if (this.apiConfig.translationLlmReasoningEffort) {
            translationLlmConfigObj.reasoning_effort = this.apiConfig.translationLlmReasoningEffort;
          }
          // 留空代表保留旧密钥；点击“清除已保存密钥”才发送空值清除。
          if (this.apiConfig.clearExistingTranslationLlmKey) {
            translationLlmConfigObj.api_key = '';
          } else if (this.apiConfig.translationLlmApiKey && this.apiConfig.translationLlmApiKey.trim() !== '') {
            translationLlmConfigObj.api_key = this.apiConfig.translationLlmApiKey;
          }
          // 序列化为JSON字符串
          config.translationLlmConfig = JSON.stringify(translationLlmConfigObj);
        }
        
        // 添加默认语言配置
        config.defaultSourceLang = this.apiConfig.defaultSourceLang || 'zh';
        config.defaultTargetLang = this.apiConfig.defaultTargetLang || 'en';
        
        // 添加摘要配置（始终保存）
        const summaryConfigObj = {
          summaryMode: this.apiConfig.summaryMode || 'disabled',  // 'disabled' | 'global' | 'dedicated' | 'textrank'
          style: this.apiConfig.summaryStyle || 'concise',
          max_length: this.apiConfig.summaryMaxLength || 150,
          prompt: this.apiConfig.summaryPrompt || '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 请直接返回JSON格式的摘要，不要添加任何markdown代码块标记、前缀或说明\n5. JSON格式示例：{json_example}\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请直接返回JSON格式的摘要：\n{json_example}'
        };
        
        // 如果启用了独立AI模式，保存独立AI配置
        if (this.apiConfig.summaryMode === 'dedicated') {
          const dedicatedLlmObj = {
            model: this.apiConfig.summaryLlmModel,
            api_url: this.apiConfig.summaryLlmUrl,
            // 保存原始类型，用于区分custom和其他类型
            original_type: this.apiConfig.summaryLlmType,
            // 如果是custom，使用summaryLlmInterfaceType；否则使用summaryLlmType
            interface_type: this.apiConfig.summaryLlmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.summaryLlmInterfaceType) : this.apiConfig.summaryLlmType,
            timeout: this.apiConfig.summaryLlmTimeout || 30,
            max_tokens: this.toPositiveInteger(this.apiConfig.summaryLlmMaxTokens, 1000),
            temperature: this.apiConfig.summaryLlmTemperature || 0.7,
            top_p: this.apiConfig.summaryLlmTopP || 1.0,
            frequency_penalty: this.apiConfig.summaryLlmFrequencyPenalty || 0,
            presence_penalty: this.apiConfig.summaryLlmPresencePenalty || 0,
            thinking_profile: this.apiConfig.summaryLlmThinkingProfile || 'auto',
            thinking_extra_body: summaryThinkingExtraBody.value
          };
          if (this.apiConfig.summaryLlmReasoningEffort) {
            dedicatedLlmObj.reasoning_effort = this.apiConfig.summaryLlmReasoningEffort;
          }
          // 留空代表保留旧密钥；点击“清除已保存密钥”才发送空值清除。
          if (this.apiConfig.clearExistingSummaryLlmKey) {
            dedicatedLlmObj.api_key = '';
          } else if (this.apiConfig.summaryLlmApiKey && this.apiConfig.summaryLlmApiKey.trim() !== '') {
            dedicatedLlmObj.api_key = this.apiConfig.summaryLlmApiKey;
          }
          summaryConfigObj.dedicated_llm = dedicatedLlmObj;
        }
        // 序列化为JSON字符串
        config.summaryConfig = JSON.stringify(summaryConfigObj);

        // 添加生图配置（始终保存）
        config.imageConfig = JSON.stringify(this.buildImageConfigObject());

        const res = await this.$http.post(this.$constant.baseURL + '/webInfo/ai/config/articleAi/save', config, true);
        
        if (res && res.code === 200) {
          this.$message.success('配置保存成功');
          // 重新加载配置以更新状态
          await this.getApiConfig();
        } else {
          this.$message.error('保存失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('保存API配置失败:', error);
        
        // 增强错误处理逻辑
        if (error.response && error.response.data) {
          const errorData = error.response.data;
          const errorMessage = errorData.message || errorData.msg || '未知错误';
          
          // 检查是否是源语言修改被拒绝的错误
          if (errorMessage.includes('源语言配置') || errorMessage.includes('文章数据')) {
            this.$message.error(errorMessage);
            // 重新检查文章状态
            this.checkArticlesExist();
            return;
          }
          
          // 检查是否是业务逻辑错误 (400状态码)
          if (error.response.status === 400) {
            this.$message.error('配置保存失败：' + errorMessage);
            return;
          }
          
          // 显示具体的服务器错误信息
          this.$message.error('保存失败：' + errorMessage);
        } else if (error.request) {
          // 网络请求发出但没有收到响应
          console.error('请求超时或网络不通:', error.request);
          this.$message.error('保存失败，请检查网络连接或服务器状态');
        } else if (error.message) {
          // 请求配置或其他错误
          console.error('请求配置错误:', error.message);
          this.$message.error('保存失败：' + error.message);
        } else {
          // 未知错误
          this.$message.error('保存失败，发生未知错误');
        }
      } finally {
        this.loading = false;
      }
    },
    
    // 检查是否存在文章数据
    async checkArticlesExist() {
      try {
        // 调用Java API检查是否有文章
        const response = await this.$http.get(this.$constant.baseURL + '/webInfo/ai/config/articleAi/hasArticles');
        
        if (response && response.code === 200) {
          // Java API直接返回boolean
          this.hasArticles = response.data === true;
          
          if (this.hasArticles) {
          }
        }
      } catch (error) {
        console.error('检查文章数据失败:', error);
        // 检查失败时保守处理，假设有文章数据
        this.hasArticles = true;
      }
    },
    // 测试翻译相关方法
    testTranslation() {
      // 验证配置完整性
      if (this.apiConfig.mode === 'llm') {
        // 使用全局AI模型时，验证全局AI配置
        if (!this.apiConfig.llmModel) {
          this.$message.warning('请先配置全局AI模型名称');
          return;
        }
        // 对于有默认URL的类型，如果URL为空则自动填充
        if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
          this.onLlmTypeChange(this.apiConfig.llmType);
        }
        // 再次检查URL（Azure和Custom需要手动配置）
        if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
          this.$message.warning('请先配置全局AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.mode === 'dedicated_llm') {
        // 使用独立AI模型时，验证独立AI配置
        if (!this.apiConfig.translationLlmModel) {
          this.$message.warning('请先配置翻译独立AI模型名称');
          return;
        }
        // 对于有默认URL的类型，如果URL为空则自动填充
        if (!this.apiConfig.translationLlmUrl || this.apiConfig.translationLlmUrl.trim() === '') {
          this.onTranslationLlmTypeChange(this.apiConfig.translationLlmType);
        }
        // 再次检查URL（Azure和Custom需要手动配置）
        if (!this.apiConfig.translationLlmUrl || this.apiConfig.translationLlmUrl.trim() === '') {
          this.$message.warning('请先配置翻译独立AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.mode === 'api') {
        if (!this.validateApiProviderConfig()) {
          return;
        }
      }
      
      // 重置表单，保留测试类型和默认内容，只清空翻译结果
      const savedTestType = this.testTranslationForm.testType || 'toon';
      this.testTranslationForm = {
        testType: savedTestType,
        sourceText: '# 人工智能简介\n\n人工智能（AI）正在改变我们的生活方式。从智能助手到自动驾驶，AI技术无处不在。\n\n## 深度学习\n\n深度学习是AI的核心技术之一，它通过神经网络模拟人脑的学习过程。',  // 保留默认单文本内容
        title: '人工智能的未来发展',  // 保留默认标题
        content: '# 人工智能简介\n\n人工智能（AI）正在改变我们的生活方式。从智能助手到自动驾驶，AI技术无处不在。\n\n## 深度学习\n\n深度学习是AI的核心技术之一，它通过神经网络模拟人脑的学习过程。',  // 保留默认内容
        translatedText: '',
        translatedTitle: '',
        translatedContent: '',
        toonTokens: null,
        formatTokens: null,
        inputFormat: '',
        responseFormat: '',
        tokenBaselineLabel: '',
        tokenSavedPercent: null,
        processingTime: null,
        detectedLang: null,
        useStream: false,  // 暂不支持流式翻译
        error: null  // 清空错误信息
      };
      this.testTranslationDialogVisible = true;
    },
    async doTestTranslation() {
      // 判断测试类型
      const isToonTest = this.testTranslationForm.testType === 'toon';
      
      // 验证输入
      if (isToonTest) {
        if (!this.testTranslationForm.title || !this.testTranslationForm.content) {
          this.$message.warning('请输入文章标题和内容');
          return;
        }
      } else {
        if (!this.testTranslationForm.sourceText) {
          this.$message.warning('请输入要翻译的文本');
          return;
        }
      }
      
      // 验证配置完整性
      if (this.apiConfig.mode === 'llm') {
        if (!this.apiConfig.llmModel) {
          this.$message.warning('请先配置全局AI模型名称');
          return;
        }
        if (!this.apiConfig.llmUrl) {
          this.$message.warning('请先配置全局AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.mode === 'dedicated_llm') {
        if (!this.apiConfig.translationLlmModel) {
          this.$message.warning('请先配置翻译独立AI模型名称');
          return;
        }
        if (!this.apiConfig.translationLlmUrl) {
          this.$message.warning('请先配置翻译独立AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.mode === 'api') {
        if (!this.validateApiProviderConfig()) {
          return;
        }
      }
      
      this.testTranslationLoading = true;
      // 清空之前的结果
      this.testTranslationForm.translatedText = '';
      this.testTranslationForm.translatedTitle = '';
      this.testTranslationForm.translatedContent = '';
      this.testTranslationForm.toonTokens = null;
      this.testTranslationForm.formatTokens = null;
      this.testTranslationForm.inputFormat = '';
      this.testTranslationForm.responseFormat = '';
      this.testTranslationForm.tokenBaselineLabel = '';
      this.testTranslationForm.tokenSavedPercent = null;
      this.testTranslationForm.error = null;
      
      try {
          await this.doNormalTranslation();
      } catch (error) {
        console.error('测试翻译失败:', error);
        if (error.message && error.message.includes('超时')) {
          this.testTranslationForm.error = '翻译请求超时，请尝试增加超时设置或检查网络连接';
        } else {
          this.$message.error('测试翻译失败：'+ (error.message || '未知错误'));
          this.testTranslationForm.error = error.message || '未知错误';
        }
      } finally {
        this.testTranslationLoading = false;
      }
    },
    
    async doNormalTranslation() {
      const startTime = Date.now();
      
      // 构建临时配置
      const tempConfig = this.buildTempConfig();
      
      // 判断测试类型，构建请求数据
      const isToonTest = this.testTranslationForm.testType === 'toon';
      const requestData = {
        config: tempConfig
      };
      
      if (isToonTest) {
        // 文章结构化测试：发送标题和内容
        requestData.title = this.testTranslationForm.title;
        requestData.content = this.testTranslationForm.content;
      } else {
        // 单文本测试
        requestData.text = this.testTranslationForm.sourceText;
      }
      
      // 调用翻译测试接口
      const response = await this.$http.post(this.$constant.baseURL + '/admin/translation/test/text', requestData, true);
      
      if (response.code === 200 && response.data) {
        this.testTranslationForm.processingTime = (Date.now() - startTime) / 1000;
        
        if (response.data.is_toon || response.data.is_article) {
          this.testTranslationForm.translatedTitle = response.data.translated_title || '';
          this.testTranslationForm.translatedContent = response.data.translated_content || '';
          this.testTranslationForm.formatTokens = response.data.format_tokens || response.data.toon_tokens;
          this.testTranslationForm.toonTokens = this.testTranslationForm.formatTokens;
          this.testTranslationForm.inputFormat = response.data.input_format || '';
          this.testTranslationForm.responseFormat = response.data.response_format || '';
          this.testTranslationForm.tokenBaselineLabel = response.data.token_baseline_label || '传统JSON';
          this.testTranslationForm.tokenSavedPercent = response.data.token_saved_percent;
          
          if (response.data.translated_title || response.data.translated_content) {
            const formatLabel = this.getDataFormatLabel(response.data.input_format || response.data.response_format || 'article');
            const baselineLabel = response.data.token_baseline_label || '传统JSON';
            const tokenMsg = response.data.token_saved_percent ?
              `，较${baselineLabel}估算节省 ${response.data.token_saved_percent}%` : '';
            this.$message.success(`${formatLabel}翻译成功${tokenMsg} (引擎: ${response.data.engine})`);
          } else {
            this.$message.warning('翻译完成但未能解析标题和内容，请检查翻译提示词');
            this.testTranslationForm.error = 'LLM 返回了无法解析的响应，请尝试调整翻译提示词或检查API配置';
          }
        } else if (response.data.translated_text) {
          this.testTranslationForm.translatedText = response.data.translated_text;
          this.$message.success(`翻译成功 (引擎: ${response.data.engine})`);
        } else {
          this.$message.warning('翻译完成但返回了意外的数据格式');
          this.testTranslationForm.error = '翻译返回了意外的数据格式，请检查配置';
        }

        this.$nextTick(() => {
          const el = this.$refs.dialogContent;
          if (el) {
            const scrollContainer = el.closest('.el-dialog__body') || el;
            scrollContainer.scrollTop = scrollContainer.scrollHeight;
          }
        });
      } else {
        this.$message.error(response.message || '翻译失败');
        throw new Error(response.message || '翻译失败');
      }
    },
    showPromptDialog(type, feature) {
      this.promptDialogType = type;
      this.promptDialogFeature = feature || 'translate';
      this.promptDialogVisible = true;
    },
    getDataFormatLabel(format) {
      const normalized = (format || '').toLowerCase();
      if (normalized === 'toon') return 'TOON';
      if (normalized === 'json') return 'JSON';
      if (normalized === 'csv') return 'CSV';
      if (normalized === 'plain') return '纯文本';
      if (normalized === 'key_value') return '键值';
      if (normalized === 'article') return '文章';
      return '结构化';
    },
    formatTime(timeString) {
      if (!timeString) return '';
      const date = new Date(timeString);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
    },
    getModelPlaceholder() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '如 gpt-5, gpt-4.1, gpt-4o, gpt-4-turbo';
        case 'anthropic':
          return '如 claude-4, claude-3-7-sonnet, claude-3-5-sonnet-20241022';
        case 'siliconflow':
          return '如 Qwen/QwQ-32B, Qwen/Qwen2.5-72B-Instruct，或直接输入任意模型名称';
        case 'deepseek':
          return '如 deepseek-v4-flash, deepseek-v4-pro';
        case 'worldrouter':
          return '如 gpt-5.4，或 WorldRouter 官方模型列表中的任意模型 ID';
        case 'azure':
          return '如 gpt-4.1, gpt-4o, gpt-4-turbo (Azure部署名称)';
        case 'custom':
          return '请输入自定义模型名称，如 qwen3:8b, qwen2.5:7b, llama3:8b, 或任何其他模型';
        default:
          return '请输入模型名称';
      }
    },
    getModelTip() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '选择OpenAI / ChatGPT API 模型：当前使用 Chat Completions 接口；GPT-5适合复杂任务，GPT-5-nano性价比高，GPT-4.1强大且稳定；当前不支持 Codex 接入';
        case 'anthropic':
          return '选择Anthropic Claude模型：Claude-4.1-opus，Claude-4.5 Sonnet性能强大，Claude-3.5系列稳定可靠，Haiku速度快';
        case 'siliconflow':
          return '可选择预设模型或直接输入自定义模型名称。推荐：QwQ-32B推理强、Qwen2.5性价比高、DeepSeek-V3性能强。支持硅基流动平台所有可用模型，API密钥从 https://siliconflow.cn 获取';
        case 'deepseek':
          return '选择DeepSeek模型：默认 deepseek-v4-flash，复杂任务可用 deepseek-v4-pro 并按需设置思考程度。API密钥从 https://platform.deepseek.com 获取';
        case 'worldrouter':
          return '选择WorldRouter模型：默认 gpt-5.4，也可输入官方模型列表中的任意模型 ID。接口按 OpenAI 兼容格式调用';
        case 'azure':
          return '输入Azure OpenAI部署的模型名称，需要与Azure门户中配置的部署名称完全一致。支持GPT-4.1等最新模型';
        case 'custom':
          return '支持多种模型和接口格式：本地模型（Ollama: qwen3、llama3等）、云端API（通义千问、文心一言等）。请选择对应的接口类型以确保正确通信';
        default:
          return '指定要使用的模型名称，确保与API服务提供商的模型名称一致';
      }
    },
    // 使用统一的后台管理语言映射工具（中文）
    getLanguageName: getAdminLanguageName,
    testSummary() {
      if (this.apiConfig.summaryMode === 'disabled') {
        this.$message.info('自动摘要已关闭，无需测试');
        return;
      }

      // 验证配置完整性（仅对AI模式）
      if (this.apiConfig.summaryMode === 'global') {
        // 使用全局AI模型时，验证全局AI配置
        if (!this.apiConfig.llmModel) {
          this.$message.warning('请先配置全局AI模型名称');
          return;
        }
        // 对于有默认URL的类型，如果URL为空则自动填充
        if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
          this.onLlmTypeChange(this.apiConfig.llmType);
        }
        // 再次检查URL（Azure和Custom需要手动配置）
        if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
          this.$message.warning('请先配置全局AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.summaryMode === 'dedicated') {
        // 使用独立AI模型时，验证独立AI配置
        if (!this.apiConfig.summaryLlmModel) {
          this.$message.warning('请先配置摘要独立AI模型名称');
          return;
        }
        // 对于有默认URL的类型，如果URL为空则自动填充
        if (!this.apiConfig.summaryLlmUrl || this.apiConfig.summaryLlmUrl.trim() === '') {
          this.onSummaryLlmTypeChange(this.apiConfig.summaryLlmType);
        }
        // 再次检查URL（Azure和Custom需要手动配置）
        if (!this.apiConfig.summaryLlmUrl || this.apiConfig.summaryLlmUrl.trim() === '') {
          this.$message.warning('请先配置摘要独立AI的API接口地址');
          return;
        }
      }
      
      // 根据摘要模式选择不同的测试对话框
      if (this.apiConfig.summaryMode === 'textrank') {
        // 本地摘录模式：打开专用对话框
        this.testTextrankForm.summaries = null;
        this.testTextrankForm.processingTime = null;
        this.testTextrankForm.error = null;
        this.testTextrankDialogVisible = true;
      } else {
        // AI模式：打开标准对话框
        this.testSummaryForm.summary = '';
        this.testSummaryForm.summaries = null;
        this.testSummaryForm.processingTime = null;
        this.testSummaryForm.method = null;
        this.testSummaryForm.toonTokens = null;
        this.testSummaryForm.tokenSavedPercent = null;
        this.testSummaryForm.error = null;
        this.testSummaryDialogVisible = true;
      }
    },
    async doTestSummary() {
      if (!this.testSummaryForm.content.trim()) {
        this.$message.warning('请输入要生成摘要的内容');
        return;
      }
      
      // 验证配置完整性
      if (this.apiConfig.summaryMode === 'global') {
        // 使用全局AI模型时，验证全局AI配置
        if (!this.apiConfig.llmModel) {
          this.$message.warning('请先配置全局AI模型名称');
          return;
        }
        if (!this.apiConfig.llmUrl) {
          this.$message.warning('请先配置全局AI的API接口地址');
          return;
        }
      } else if (this.apiConfig.summaryMode === 'dedicated') {
        // 使用独立AI模型时，验证独立AI配置
        if (!this.apiConfig.summaryLlmModel) {
          this.$message.warning('请先配置摘要独立AI模型名称');
          return;
        }
        if (!this.apiConfig.summaryLlmUrl) {
          this.$message.warning('请先配置摘要独立AI的API接口地址');
          return;
        }
      }
      
      this.testSummaryLoading = true;
      this.testSummaryForm.summary = '';
      this.testSummaryForm.summaries = null;
      this.testSummaryForm.error = null;
      
      try {
        // 构建临时配置，用于测试未保存的配置
        const tempConfig = this.buildTempConfig();
        
        // 使用源语言构建测试请求（多语言格式）
        const sourceLanguage = this.apiConfig.defaultSourceLang || 'zh';
        const targetLanguage = this.apiConfig.defaultTargetLang || 'en';
        
        // 构建languages对象，包含源语言和目标语言
        const languages = {
          [sourceLanguage]: this.testSummaryForm.content
        };
        
        // 如果目标语言与源语言不同，添加目标语言（内容为空，让AI生成）
        if (targetLanguage !== sourceLanguage) {
          languages[targetLanguage] = '';
        }
        
        const testRequest = {
          article_id: 0,  // 测试用，ID为0
          languages: languages,
          max_length: this.apiConfig.summaryMaxLength,
          style: this.apiConfig.summaryStyle,
          config: tempConfig  // 添加临时配置
        };
        
        // 前端超时时间 = 配置的超时时间 + 10秒缓冲
        const timeoutMs = ((this.apiConfig.llmTimeout || 30) + 10) * 1000;
        
        const res = await this.$http.post(this.$constant.baseURL + '/admin/translation/test/summary', testRequest, true);
        
        if (res && res.code === 200 && res.data) {
          const result = res.data;
          
          if (result.success && result.summaries) {
            this.testSummaryForm.summaries = result.summaries;
            this.testSummaryForm.processingTime = result.processing_time;
            this.testSummaryForm.method = result.method;
            this.testSummaryForm.toonTokens = result.toon_tokens;
            this.testSummaryForm.tokenSavedPercent = result.token_saved_percent;
            
            // 计算生成了多少种语言的摘要
            const langCount = Object.keys(result.summaries).length;
            const tokenInfo = result.token_saved_percent ? `，节省${result.token_saved_percent}% token` : '';
            this.$message.success(`摘要生成成功！生成了${langCount}种语言${tokenInfo}`);
          } else {
            this.testSummaryForm.error = result.error_message || '摘要生成失败';
            this.$message.error('摘要生成失败：' + this.testSummaryForm.error);
          }
        } else {
          this.testSummaryForm.error = res?.message || '网络错误';
          this.$message.error('摘要测试失败：' + this.testSummaryForm.error);
        }
      } catch (error) {
        console.error('摘要测试失败:', error);
        this.testSummaryForm.error = error.message || '网络连接失败';
        this.$message.error('摘要测试失败，请检查网络连接和配置');
      } finally {
        this.testSummaryLoading = false;
      }
    },
    resetSummaryTest() {
      this.testSummaryForm.summary = '';
      this.testSummaryForm.summaries = null;
      this.testSummaryForm.processingTime = null;
      this.testSummaryForm.method = null;
      this.testSummaryForm.toonTokens = null;
      this.testSummaryForm.tokenSavedPercent = null;
      this.testSummaryForm.error = null;
    },
    
    // 本地摘录测试方法
    async doTestTextrank() {
      if (!this.testTextrankForm.sourceContent.trim() && !this.testTextrankForm.targetContent.trim()) {
        this.$message.warning('请至少输入一种语言的内容');
        return;
      }
      
      this.testTextrankLoading = true;
      this.testTextrankForm.summaries = null;
      this.testTextrankForm.error = null;
      
      try {
        const sourceLanguage = this.apiConfig.defaultSourceLang || 'zh';
        const targetLanguage = this.apiConfig.defaultTargetLang || 'en';
        
        // 构建languages对象，只包含有内容的语言
        const languages = {};
        if (this.testTextrankForm.sourceContent.trim()) {
          languages[sourceLanguage] = this.testTextrankForm.sourceContent;
        }
        if (this.testTextrankForm.targetContent.trim()) {
          languages[targetLanguage] = this.testTextrankForm.targetContent;
        }
        
        const testRequest = {
          article_id: 0,
          languages: languages,
          max_length: this.apiConfig.summaryMaxLength,
          style: this.apiConfig.summaryStyle,
          config: this.buildTempConfig()
        };
        
        const startTime = Date.now();
        const res = await this.$http.post(this.$constant.baseURL + '/admin/translation/test/summary', testRequest, true);
        
        if (res && res.code === 200 && res.data) {
          const result = res.data;
          
          if (result.success && result.summaries) {
            this.testTextrankForm.summaries = result.summaries;
            this.testTextrankForm.processingTime = result.processing_time;
            
            const langCount = Object.keys(result.summaries).length;
            this.$message.success(`摘录生成成功！生成了${langCount}种语言的摘录`);
          } else {
            this.testTextrankForm.error = result.error_message || '摘录生成失败';
            this.$message.error('摘录生成失败：' + this.testTextrankForm.error);
          }
        } else {
          this.testTextrankForm.error = res?.message || '网络错误';
          this.$message.error('摘要测试失败：' + this.testTextrankForm.error);
        }
      } catch (error) {
        console.error('摘要测试失败:', error);
        this.testTextrankForm.error = error.message || '网络连接失败';
        this.$message.error('摘要测试失败，请检查网络连接');
      } finally {
        this.testTextrankLoading = false;
      }
    },
    
    resetTextrankTest() {
      this.testTextrankForm.summaries = null;
      this.testTextrankForm.processingTime = null;
      this.testTextrankForm.error = null;
    },
    
    // 当LLM类型改变时，自动设置默认URL和模型
    onLlmTypeChange(newType) {
      // 默认配置
      const defaultUrls = {
        'openai': 'https://api.openai.com/v1',
        'anthropic': 'https://api.anthropic.com/v1/messages',
        'siliconflow': 'https://api.siliconflow.cn/v1',
        'openrouter': 'https://openrouter.ai/api/v1',
        'worldrouter': 'https://inference-api.worldrouter.ai/v1',
        'deepseek': 'https://api.deepseek.com/v1',
        'azure': '',  // Azure需要自定义URL
        'custom': ''  // 自定义需要手动填写
      };
      
      const defaultModels = {
        'openai': 'gpt-4o-mini',
        'anthropic': 'claude-3-5-sonnet-20241022',
        'siliconflow': 'Qwen/Qwen3-8B',
        'openrouter': 'openai/gpt-4o-mini',
        'worldrouter': 'gpt-5.4',
        'deepseek': 'deepseek-v4-flash',
        'azure': 'gpt-4',
        'custom': ''
      };
      
      // 智能恢复：如枟切换回后端保存的类型，使用后端保存的配置
      if (this.savedLlmConfig && newType === this.savedLlmConfig.type) {
        // 如果后端保存的model不为空，使用后端的；否则使用默认值
        this.apiConfig.llmModel = this.savedLlmConfig.model || defaultModels[newType] || '';
        // 如果后端保存的url不为空，使用后端的；否则使用默认值
        this.apiConfig.llmUrl = this.savedLlmConfig.url || defaultUrls[newType] || '';
        // 如果是自定义类型，恢复接口类型
        if (newType === 'custom' && this.savedLlmConfig.interfaceType) {
          this.apiConfig.llmInterfaceType = this.normalizeCustomInterfaceType(this.savedLlmConfig.interfaceType);
        }
        this.apiConfig.llmReasoningEffort = this.savedLlmConfig.reasoningEffort || '';
        this.apiConfig.llmThinkingProfile = this.savedLlmConfig.thinkingProfile || 'auto';
        this.apiConfig.llmThinkingExtraBodyText = this.savedLlmConfig.thinkingExtraBodyText || '';
        return;
      }
      
      // 否则使用默认配置
      if (defaultUrls[newType] !== undefined) {
        this.apiConfig.llmUrl = defaultUrls[newType];
      }
      
      if (defaultModels[newType] !== undefined) {
        this.apiConfig.llmModel = defaultModels[newType];
      }
      if (newType === 'custom') {
        this.apiConfig.llmInterfaceType = this.normalizeCustomInterfaceType(this.apiConfig.llmInterfaceType);
      }
      this.apiConfig.llmReasoningEffort = '';
      if (newType === 'openrouter' && this.apiConfig.llmThinkingProfile === 'auto') {
        this.apiConfig.llmThinkingProfile = 'openrouter';
      }
      if (newType === 'worldrouter' && this.apiConfig.llmThinkingProfile === 'auto') {
        this.apiConfig.llmThinkingProfile = 'worldrouter';
      }
    },
    
    // 当翻译独立LLM类型改变时，自动设置默认URL和模型
    onTranslationLlmTypeChange(newType) {
      // 默认配置
      const defaultUrls = {
        'openai': 'https://api.openai.com/v1',
        'anthropic': 'https://api.anthropic.com/v1/messages',
        'siliconflow': 'https://api.siliconflow.cn/v1',
        'openrouter': 'https://openrouter.ai/api/v1',
        'worldrouter': 'https://inference-api.worldrouter.ai/v1',
        'deepseek': 'https://api.deepseek.com/v1',
        'azure': '',
        'custom': ''
      };
      
      const defaultModels = {
        'openai': 'gpt-4o-mini',
        'anthropic': 'claude-3-5-sonnet-20241022',
        'siliconflow': 'Qwen/Qwen3-8B',
        'openrouter': 'openai/gpt-4o-mini',
        'worldrouter': 'gpt-5.4',
        'deepseek': 'deepseek-v4-flash',
        'azure': 'gpt-4',
        'custom': ''
      };
      
      // 智能恢复：如果切换回后端保存的类型，使用后端保存的配置
      if (this.savedTranslationLlmConfig && newType === this.savedTranslationLlmConfig.type) {
        // 如果后端保存的model不为空，使用后端的；否则使用默认值
        this.apiConfig.translationLlmModel = this.savedTranslationLlmConfig.model || defaultModels[newType] || '';
        // 如果后端保存的url不为空，使用后端的；否则使用默认值
        this.apiConfig.translationLlmUrl = this.savedTranslationLlmConfig.url || defaultUrls[newType] || '';
        // 如果是自定义类型，恢复接口类型
        if (newType === 'custom' && this.savedTranslationLlmConfig.interfaceType) {
          this.apiConfig.translationLlmInterfaceType = this.normalizeCustomInterfaceType(this.savedTranslationLlmConfig.interfaceType);
        }
        this.apiConfig.translationLlmReasoningEffort = this.savedTranslationLlmConfig.reasoningEffort || '';
        this.apiConfig.translationLlmThinkingProfile = this.savedTranslationLlmConfig.thinkingProfile || 'auto';
        this.apiConfig.translationLlmThinkingExtraBodyText = this.savedTranslationLlmConfig.thinkingExtraBodyText || '';
        return;
      }
      
      // 否则使用默认配置
      if (defaultUrls[newType] !== undefined) {
        this.apiConfig.translationLlmUrl = defaultUrls[newType];
      }
      
      if (defaultModels[newType] !== undefined) {
        this.apiConfig.translationLlmModel = defaultModels[newType];
      }
      if (newType === 'custom') {
        this.apiConfig.translationLlmInterfaceType = this.normalizeCustomInterfaceType(this.apiConfig.translationLlmInterfaceType);
      }
      this.apiConfig.translationLlmReasoningEffort = '';
      if (newType === 'openrouter' && this.apiConfig.translationLlmThinkingProfile === 'auto') {
        this.apiConfig.translationLlmThinkingProfile = 'openrouter';
      }
      if (newType === 'worldrouter' && this.apiConfig.translationLlmThinkingProfile === 'auto') {
        this.apiConfig.translationLlmThinkingProfile = 'worldrouter';
      }
    },
    
    // 当摘要独立LLM类型改变时，自动设置默认URL和模型
    onSummaryLlmTypeChange(newType) {
      // 默认配置
      const defaultUrls = {
        'openai': 'https://api.openai.com/v1',
        'anthropic': 'https://api.anthropic.com/v1/messages',
        'siliconflow': 'https://api.siliconflow.cn/v1',
        'openrouter': 'https://openrouter.ai/api/v1',
        'worldrouter': 'https://inference-api.worldrouter.ai/v1',
        'deepseek': 'https://api.deepseek.com/v1',
        'azure': '',
        'custom': ''
      };
      
      const defaultModels = {
        'openai': 'gpt-4o-mini',
        'anthropic': 'claude-3-5-sonnet-20241022',
        'siliconflow': 'Qwen/Qwen3-8B',
        'openrouter': 'openai/gpt-4o-mini',
        'worldrouter': 'gpt-5.4',
        'deepseek': 'deepseek-v4-flash',
        'azure': 'gpt-4',
        'custom': ''
      };
      
      // 智能恢复：如果切换回后端保存的类型，使用后端保存的配置
      if (this.savedSummaryLlmConfig && newType === this.savedSummaryLlmConfig.type) {
        // 如果后端保存的model不为空，使用后端的；否则使用默认值
        this.apiConfig.summaryLlmModel = this.savedSummaryLlmConfig.model || defaultModels[newType] || '';
        // 如果后端保存的url不为空，使用后端的；否则使用默认值
        this.apiConfig.summaryLlmUrl = this.savedSummaryLlmConfig.url || defaultUrls[newType] || '';
        // 如果是自定义类型，恢复接口类型
        if (newType === 'custom' && this.savedSummaryLlmConfig.interfaceType) {
          this.apiConfig.summaryLlmInterfaceType = this.normalizeCustomInterfaceType(this.savedSummaryLlmConfig.interfaceType);
        }
        this.apiConfig.summaryLlmReasoningEffort = this.savedSummaryLlmConfig.reasoningEffort || '';
        this.apiConfig.summaryLlmThinkingProfile = this.savedSummaryLlmConfig.thinkingProfile || 'auto';
        this.apiConfig.summaryLlmThinkingExtraBodyText = this.savedSummaryLlmConfig.thinkingExtraBodyText || '';
        return;
      }
      
      // 否则使用默认配置
      if (defaultUrls[newType] !== undefined) {
        this.apiConfig.summaryLlmUrl = defaultUrls[newType];
      }
      
      if (defaultModels[newType] !== undefined) {
        this.apiConfig.summaryLlmModel = defaultModels[newType];
      }
      if (newType === 'custom') {
        this.apiConfig.summaryLlmInterfaceType = this.normalizeCustomInterfaceType(this.apiConfig.summaryLlmInterfaceType);
      }
      this.apiConfig.summaryLlmReasoningEffort = '';
      if (newType === 'openrouter' && this.apiConfig.summaryLlmThinkingProfile === 'auto') {
        this.apiConfig.summaryLlmThinkingProfile = 'openrouter';
      }
      if (newType === 'worldrouter' && this.apiConfig.summaryLlmThinkingProfile === 'auto') {
        this.apiConfig.summaryLlmThinkingProfile = 'worldrouter';
      }
    },

    // 生图服务商切换时自动填充默认URL和模型
    onImageProviderChange(provider) {
      const defaults = {
        'openai': { url: 'https://api.openai.com/v1/images/generations', model: 'gpt-image-2' },
        'siliconflow': { url: 'https://api.siliconflow.cn/v1/images/generations', model: 'Qwen/Qwen-Image' },
        'doubao': { url: 'https://ark.cn-beijing.volces.com/api/v3/images/generations', model: 'doubao-seedream-5.0-lite' },
        'dashscope': { url: 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation', model: 'wan2.7-image' },
        'gemini': { url: 'https://generativelanguage.googleapis.com/v1beta', model: 'gemini-3-pro-image-preview' },
        'custom': { url: '', model: '' }
      };
      const def = defaults[provider];
      if (def) {
        this.apiConfig.imageUrl = def.url;
        this.apiConfig.imageModel = def.model;
      }
    },

    // 切换宽高比时，若当前分辨率不匹配新比例，自动选中第一个预设
    onImageSizeChange() {
      const presets = this.imageResolutionPresets;
      if (!presets.includes(this.apiConfig.imageResolution)) {
        this.apiConfig.imageResolution = presets[0];
      }
      this.imageResolutionError = '';
    },

    // 校验分辨率输入：格式 WxH，比例须与 imageSize 一致
    validateImageResolution() {
      const val = (this.apiConfig.imageResolution || '').trim();
      const ratio = this.apiConfig.imageSize;
      if (!val) {
        this.imageResolutionError = '请填写分辨率，如 1920x1080';
        return false;
      }
      const m = /^(\d+)\s*[x×*]\s*(\d+)$/i.exec(val);
      if (!m) {
        this.imageResolutionError = '格式错误，应为 宽x高，如 1920x1080';
        return false;
      }
      const w = parseInt(m[1], 10);
      const h = parseInt(m[2], 10);
      if (w <= 0 || h <= 0) {
        this.imageResolutionError = '宽高必须为正整数';
        return false;
      }
      const ratioMap = { '1:1': [1, 1], '16:9': [16, 9], '9:16': [9, 16], '4:3': [4, 3], '3:4': [3, 4] };
      const r = ratioMap[ratio];
      if (r) {
        const [rw, rh] = r;
        // 允许小数误差（如 1536x864 是 16:9，但 1536/864=1.777...）
        const delta = Math.abs(w / h - rw / rh);
        if (delta > 0.01) {
          this.imageResolutionError = `比例与 ${ratio} 不一致，请调整宽高`;
          return false;
        }
      }
      this.imageResolutionError = '';
      return true;
    },

    // 生图独立AI模型类型切换时自动填充默认URL
    onImageLlmTypeChange(newType) {
      const defaultUrls = {
        'openai': 'https://api.openai.com/v1',
        'anthropic': 'https://api.anthropic.com/v1/messages',
        'siliconflow': 'https://api.siliconflow.cn/v1',
        'openrouter': 'https://openrouter.ai/api/v1',
        'worldrouter': 'https://inference-api.worldrouter.ai/v1',
        'deepseek': 'https://api.deepseek.com/v1',
        'azure': '',
        'custom': ''
      };
      const defaultModels = {
        'openai': 'gpt-4o-mini',
        'anthropic': 'claude-3-5-sonnet-20241022',
        'siliconflow': 'Qwen/Qwen3-8B',
        'openrouter': 'openai/gpt-4o-mini',
        'worldrouter': 'gpt-5.4',
        'deepseek': 'deepseek-v4-flash',
        'azure': 'gpt-4',
        'custom': ''
      };
      if (defaultUrls[newType] !== undefined) {
        this.apiConfig.imageLlmUrl = defaultUrls[newType];
      }
      if (defaultModels[newType] !== undefined) {
        this.apiConfig.imageLlmModel = defaultModels[newType];
      }
    },

    // 打开生图测试对话框
    testImage() {
      if (this.apiConfig.imageMode === 'disabled') {
        this.$message.info('生图功能已关闭，无需测试');
        return;
      }
      if (!this.apiConfig.imageModel) {
        this.$message.warning('请先配置生图模型名称');
        return;
      }
      if (!this.apiConfig.imageUrl) {
        this.$message.warning('请先配置生图API接口地址');
        return;
      }
      // 独立AI模式验证
      if (this.apiConfig.imageMode === 'dedicated') {
        if (!this.apiConfig.imageLlmModel) {
          this.$message.warning('请先配置生图独立AI模型名称');
          return;
        }
        if (!this.apiConfig.imageLlmUrl) {
          this.$message.warning('请先配置生图独立AI接口地址');
          return;
        }
      }
      // 重置上次结果
      this.testImageForm.imageUrl = '';
      this.testImageForm.prompt = '';
      this.testImageForm.durationMs = null;
      this.testImageForm.form = '';
      this.testImageForm.provider = '';
      this.testImageForm.error = null;
      this.testImageDialogVisible = true;
    },

    // 执行生图测试（带文章内容）
    async doTestImage() {
      if (!this.testImageForm.title && !this.testImageForm.content) {
        this.$message.warning('请至少填写标题或内容');
        return;
      }

      this.testImageLoading = true;
      this.testImageForm.imageUrl = '';
      this.testImageForm.prompt = '';
      this.testImageForm.durationMs = null;
      this.testImageForm.form = '';
      this.testImageForm.provider = '';
      this.testImageForm.error = null;

      try {
        const imageConfigObj = this.buildImageConfigObject();
        const tempConfig = {
          configType: 'article_ai',
          configName: 'default',
          imageConfig: JSON.stringify(imageConfigObj)
        };
        
        if (this.apiConfig.imageMode === 'global') {
          tempConfig.llmConfig = JSON.stringify({
            type: this.apiConfig.llmType,
            model: this.apiConfig.llmModel,
            interface_type: this.apiConfig.llmInterfaceType,
            api_url: this.apiConfig.llmUrl,
            api_key: this.apiConfig.hasExistingLlmKey && !this.apiConfig.llmApiKey ? '***' : this.apiConfig.llmApiKey,
            timeout: this.apiConfig.llmTimeout || 60,
            thinking_profile: this.apiConfig.llmThinkingProfile || 'auto',
            thinking_extra_body_text: this.apiConfig.llmThinkingExtraBodyText || '',
            reasoning_effort: this.apiConfig.llmReasoningEffort || ''
          });
        }
        
        // title/content 通过 query 参数传递，config 通过 body
        const params = new URLSearchParams();
        if (this.testImageForm.title) params.append('title', this.testImageForm.title);
        if (this.testImageForm.content) params.append('content', this.testImageForm.content);
        const url = this.$constant.baseURL + '/webInfo/ai/config/articleAi/testImage?' + params.toString();
        const res = await this.$http.post(url, tempConfig, true, true, 120000);

        if (res && res.code === 200 && res.data) {
          const result = res.data;
          if (result.success) {
            this.testImageForm.imageUrl = result.url || '';
            this.testImageForm.prompt = result.prompt || '';
            this.testImageForm.durationMs = result.durationMs != null ? result.durationMs : null;
            this.testImageForm.form = result.form || '';
            this.testImageForm.provider = result.provider || '';
            this.$message.success('生图成功');
          } else {
            this.testImageForm.error = result.message || result.error || '未知错误';
          }
        } else {
          this.testImageForm.error = (res && res.message) ? res.message : '网络错误';
        }
      } catch (error) {
        console.error('生图测试失败:', error);
        const errMsg = error.response?.data?.message || error.message || '网络连接失败';
        this.testImageForm.error = errMsg;
      } finally {
        this.testImageLoading = false;
      }
    },

    // 下载测试生成的图片（兼容 data URI 和 http URL）
    downloadTestImage() {
      if (!this.testImageForm.imageUrl) return;
      const link = document.createElement('a');
      link.href = this.testImageForm.imageUrl;
      link.download = 'ai_cover_test_' + Date.now() + '.png';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    },

    // 构建imageConfig对象（供保存和测试共用）
    buildImageConfigObject() {
      const obj = {
        imageMode: this.apiConfig.imageMode || 'disabled',
        provider: this.apiConfig.imageProvider || 'siliconflow',
        model: this.apiConfig.imageModel || '',
        api_url: this.apiConfig.imageUrl || '',
        size: this.apiConfig.imageSize || '16:9',
        resolution: this.apiConfig.imageResolution || '1536x864',
        quality: this.apiConfig.imageQuality || 'auto',
        cover_template: this.apiConfig.coverTemplate || 'object',
        custom_refine_prompt: this.apiConfig.customRefinePrompt || '',
        timeout: this.apiConfig.imageTimeout || 120
      };
      // 生图API密钥
      if (this.apiConfig.clearExistingImageKey) {
        obj.api_key = '';
      } else if (this.apiConfig.imageApiKey && this.apiConfig.imageApiKey.trim() !== '') {
        obj.api_key = this.apiConfig.imageApiKey;
      }
      // 独立AI配置
      if (this.apiConfig.imageMode === 'dedicated') {
        obj.dedicated_llm = {
          model: this.apiConfig.imageLlmModel,
          api_url: this.apiConfig.imageLlmUrl,
          interface_type: this.apiConfig.imageLlmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.imageLlmInterfaceType) : this.apiConfig.imageLlmType,
          original_type: this.apiConfig.imageLlmType,
          timeout: this.apiConfig.imageLlmTimeout || 30
        };
        if (this.apiConfig.clearExistingImageLlmKey) {
          obj.dedicated_llm.api_key = '';
        } else if (this.apiConfig.imageLlmApiKey && this.apiConfig.imageLlmApiKey.trim() !== '') {
          obj.dedicated_llm.api_key = this.apiConfig.imageLlmApiKey;
        }
      }
      return obj;
    },

    async testGlobalAi() {
      // 先清空之前的错误（在验证之前）
      this.testGlobalAiError = null;
      
      // 验证必填字段
      if (!this.apiConfig.llmModel) {
        this.$message.warning('请先配置模型名称');
        return;
      }
      // 对于有默认URL的类型，如果URL为空则自动填充
      if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
        this.onLlmTypeChange(this.apiConfig.llmType);
      }
      // 再次检查URL（Azure和Custom需要手动配置）
      if (!this.apiConfig.llmUrl || this.apiConfig.llmUrl.trim() === '') {
        this.$message.warning('请先配置API接口地址');
        return;
      }
      
      this.testGlobalAiLoading = true;
      
      try {
        const startTime = Date.now();
        
        // 构建临时配置，用于测试未保存的配置
        const tempConfig = this.buildTempConfig();
        
        // 调用快速连接测试接口
        const response = await this.$http.post(this.$constant.baseURL + '/admin/translation/test/connection', {
          text: 'Hi',  // 极简测试文本，加快响应速度
          config: tempConfig
        }, true);
        
        if (response.code === 200) {
          const processingTime = ((Date.now() - startTime) / 1000).toFixed(2);
          this.$message.success({
            message: `全局AI模型连接成功！用时 ${processingTime}秒`,
            duration: 3000
          });
        } else {
          // 既显示在按钮右边，也弹出通知
          const errorMsg = response.message || '未知错误';
          this.testGlobalAiError = errorMsg;
          this.$message.error('连接测试失败：' + errorMsg);
        }
      } catch (error) {
        console.error('测试全局AI连接失败:', error);
        // 既显示在按钮右边，也弹出通知
        const errorMsg = error.message || '连接失败，请检查配置和网络连接';
        this.testGlobalAiError = errorMsg;
        this.$message.error('连接测试失败：' + errorMsg);
      } finally {
        this.testGlobalAiLoading = false;
      }
    },
    
    // 构建临时配置
    buildTempConfig() {
      const llmThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.llmThinkingExtraBodyText, '全局模型自定义请求参数');
      const translationThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.translationLlmThinkingExtraBodyText, '翻译独立模型自定义请求参数');
      const summaryThinkingExtraBody = this.parseJsonObjectField(this.apiConfig.summaryLlmThinkingExtraBodyText, '摘要独立模型自定义请求参数');
      if (!llmThinkingExtraBody.valid || !translationThinkingExtraBody.valid || !summaryThinkingExtraBody.valid) {
        throw new Error((!llmThinkingExtraBody.valid && llmThinkingExtraBody.message)
          || (!translationThinkingExtraBody.valid && translationThinkingExtraBody.message)
          || summaryThinkingExtraBody.message);
      }

      const config = {
        type: this.apiConfig.mode,  // 'none', 'api', 'llm', 'dedicated_llm'
        default_source_lang: this.apiConfig.defaultSourceLang || 'zh',
        default_target_lang: this.apiConfig.defaultTargetLang || 'en'
      };
      
      // 全局LLM配置（总是包含）
      config.llm = {
        model: this.apiConfig.llmModel,
        api_url: this.apiConfig.llmUrl,
        interface_type: this.apiConfig.llmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.llmInterfaceType) : this.apiConfig.llmType,
        timeout: this.apiConfig.llmTimeout || 30,
        max_tokens: this.toPositiveInteger(this.apiConfig.llmMaxTokens, 1000),
        temperature: this.apiConfig.llmTemperature || 0.7,
        top_p: this.apiConfig.llmTopP || 1.0,
        frequency_penalty: this.apiConfig.llmFrequencyPenalty || 0,
        presence_penalty: this.apiConfig.llmPresencePenalty || 0,
        thinking_profile: this.apiConfig.llmThinkingProfile || 'auto',
        thinking_extra_body: llmThinkingExtraBody.value,
        prompt: this.apiConfig.llmPrompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}'
      };
      if (this.apiConfig.llmReasoningEffort) {
        config.llm.reasoning_effort = this.apiConfig.llmReasoningEffort;
      }
      // 只有输入了新密钥才包含api_key字段
      if (this.apiConfig.llmApiKey && this.apiConfig.llmApiKey.trim() !== '') {
        config.llm.api_key = this.apiConfig.llmApiKey;
      }
      
      // 根据翻译模式添加特定配置
      if (this.apiConfig.mode === 'api') {
        config.provider = this.apiConfig.provider;
        config[this.apiConfig.provider] = this.buildApiProviderConfig();
      } else if (this.apiConfig.mode === 'dedicated_llm') {
        config.translation_llm = {
          model: this.apiConfig.translationLlmModel,
          api_url: this.apiConfig.translationLlmUrl,
          interface_type: this.apiConfig.translationLlmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.translationLlmInterfaceType) : this.apiConfig.translationLlmType,
          timeout: this.apiConfig.translationLlmTimeout || 30,
          max_tokens: this.toPositiveInteger(this.apiConfig.translationLlmMaxTokens, 1000),
          temperature: this.apiConfig.translationLlmTemperature || 0.7,
          top_p: this.apiConfig.translationLlmTopP || 1.0,
          frequency_penalty: this.apiConfig.translationLlmFrequencyPenalty || 0,
          presence_penalty: this.apiConfig.translationLlmPresencePenalty || 0,
          thinking_profile: this.apiConfig.translationLlmThinkingProfile || 'auto',
          thinking_extra_body: translationThinkingExtraBody.value,
          prompt: this.apiConfig.llmPrompt || '请将以下{source_lang}文本翻译为{target_lang}，保持原意和格式，只返回翻译结果：\n\n{toon_data}'
        };
        if (this.apiConfig.translationLlmReasoningEffort) {
          config.translation_llm.reasoning_effort = this.apiConfig.translationLlmReasoningEffort;
        }
        // 只有输入了新密钥才包含
        if (this.apiConfig.translationLlmApiKey && this.apiConfig.translationLlmApiKey.trim() !== '') {
          config.translation_llm.api_key = this.apiConfig.translationLlmApiKey;
        }
      }
      
      // 添加摘要配置
      config.summary = {
        summaryMode: this.apiConfig.summaryMode || 'disabled',  // 'disabled' | 'global' | 'dedicated' | 'textrank'
        ai_enabled: this.apiConfig.summaryMode === 'global' || this.apiConfig.summaryMode === 'dedicated',
        style: this.apiConfig.summaryStyle || 'concise',
        max_length: this.apiConfig.summaryMaxLength || 150,
        prompt: this.apiConfig.summaryPrompt || '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 保持TOON格式结构不变（2个空格缩进）\n5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记\n\n文章内容：\n\n{source_content}\n\n请返回TOON格式的摘要，格式如下：\n{toon_example}'
      };
      
      // 如果使用独立AI模式，添加独立AI配置
      if (this.apiConfig.summaryMode === 'dedicated') {
        config.summary.dedicated_llm = {
          model: this.apiConfig.summaryLlmModel,
          api_url: this.apiConfig.summaryLlmUrl,
          interface_type: this.apiConfig.summaryLlmType === 'custom' ? this.normalizeCustomInterfaceType(this.apiConfig.summaryLlmInterfaceType) : this.apiConfig.summaryLlmType,
          timeout: this.apiConfig.summaryLlmTimeout || 30,
          max_tokens: this.toPositiveInteger(this.apiConfig.summaryLlmMaxTokens, 1000),
          temperature: this.apiConfig.summaryLlmTemperature || 0.7,
          top_p: this.apiConfig.summaryLlmTopP || 1.0,
          frequency_penalty: this.apiConfig.summaryLlmFrequencyPenalty || 0,
          presence_penalty: this.apiConfig.summaryLlmPresencePenalty || 0,
          thinking_profile: this.apiConfig.summaryLlmThinkingProfile || 'auto',
          thinking_extra_body: summaryThinkingExtraBody.value
        };
        if (this.apiConfig.summaryLlmReasoningEffort) {
          config.summary.dedicated_llm.reasoning_effort = this.apiConfig.summaryLlmReasoningEffort;
        }
        // 只有输入了新密钥才包含
        if (this.apiConfig.summaryLlmApiKey && this.apiConfig.summaryLlmApiKey.trim() !== '') {
          config.summary.dedicated_llm.api_key = this.apiConfig.summaryLlmApiKey;
        }
      }
      
      return config;
    }

  }
};
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
  
.translation-management {
  min-height: calc(100vh - 60px);
}

/* 页面标题区域 */
.page-header {
  margin-bottom: 20px;
}

/* 配置容器 */
.config-container {
  border-radius: 5px;
}

.config-form {
  padding: 0;
}

/* 配置分组 */
.config-section {
  border-bottom: 1px solid #f7fafc;
}

.config-section:last-child {
  border-bottom: none;
}

.section-header {
  background: #fafafa;
  padding: 16px 24px;
  border-bottom: 1px solid #EBEEF5;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin: 0;
}

.section-content {
}



/* 表单元素 */
.full-width {
  width: 100%;
  max-width: 200px;
}

.resolution-input {
  width: 100%;
  max-width: 240px;
}

.resolution-presets {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.resolution-presets .presets-label {
  font-size: 12px;
  color: #909399;
}

.resolution-presets .preset-tag {
  cursor: pointer;
}

.number-input {
  width: 180px;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item__label {
  font-weight: 500;
  color: #606266;
  font-size: 14px;
}

.input-field .el-input__inner,
.textarea-field .el-textarea__inner {
  border: 1px solid #DCDFE6;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 14px;
  transition: border-color 0.2s ease;
  background: #ffffff;
}

.input-field .el-input__inner:focus,
.textarea-field .el-textarea__inner:focus {
  border-color: #409EFF;
  outline: none;
}

.input-field .el-input__inner:hover,
.textarea-field .el-textarea__inner:hover {
  border-color: #C0C4CC;
}

/* 选择框选项 */
.option-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-content i {
  color: #4a5568;
  opacity: 0.8;
}

/* 表单提示 */
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  padding: 8px 12px;
  background: #F5F7FA;
  border-radius: 4px;
}

.secret-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.custom-model-tip {
  background: #F5F7FA;
  color: #303133;
}

/* 自定义模型选择 */
.custom-model-select .el-input__inner {
  border-color: #409EFF !important;
  background: #F5F7FA !important;
}

/* 超时设置组 */
.timeout-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeout-input {
  width: 120px;
}

.timeout-input .el-input__inner {
  border: 1px solid #DCDFE6;
  border-radius: 4px 0 0 4px;
  background: #ffffff;
  transition: border-color 0.2s ease;
}

.timeout-input .el-input__inner:focus {
  border-color: #409EFF;
}

.timeout-input .el-input-group__append {
  background: #F5F7FA;
  border: 1px solid #DCDFE6;
  border-left: none;
  border-radius: 0 4px 4px 0;
  color: #909399;
  font-weight: 500;
  padding: 0 12px;
}

/* 信息面板 */
.info-panel {
  margin-top: 16px;
  padding: 16px;
  background: #F5F7FA;
  border-radius: 4px;
  border: 1px solid #EBEEF5;
}

.info-header {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}

/* 操作按钮区域 */
.action-bar {
  padding: 20px 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* 测试对话框基础样式 */
.test-dialog .el-dialog {
  border-radius: 4px;
}

.dialog-content {
  padding: 0;
  height: 100%;
}

.test-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
}

/* 对话框标题 */
.dialog-title-custom {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-title-custom .title-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

/* TOON提示 */
.toon-hint {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #409eff;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.toon-hint i {
  font-size: 16px;
}

/* 语言配置已移至标题区域，不再需要独立样式 */

/* 摘要信息区域样式已移除（已移至标题区域） */

.input-tips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

/* 输入部分 */
.input-section,
.stream-mode,
.translate-section,
.test-section,
.result-section,
.error-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-section label,
.stream-mode label,
.result-section label,
.error-section label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

/* 流式模式选择 */
.stream-options {
  display: flex;
  gap: 20px;
  margin-top: 8px;
}

.stream-radio .el-radio__label {
  font-weight: 400;
  color: #718096;
}

/* 翻译按钮 */
.translate-btn,
.test-btn {
}

.translate-btn:hover,
.test-btn:hover {
}

.translate-btn i,
.test-btn i {
  margin-right: 6px;
}

/* 结果输出 */
.result-output .el-textarea__inner {
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-start;
}

.result-meta .el-tag {
  font-size: 12px;
  border-radius: 4px;
}

.result-meta .el-tag i {
  margin-right: 4px;
}

/* 生图预览 */
.image-preview-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.image-preview {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.image-preview .el-image__inner {
  max-height: 400px;
  border-radius: 8px;
}

.image-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 对话框底部 */
.dialog-footer {
  padding: 16px 24px;
  background: #f7fafc;
  text-align: right;
  border-top: 1px solid #e2e8f0;
}

.dialog-footer .el-button {
  padding: 8px 16px;
  border-radius: 6px;
}

/* 暗色模式适配 - 测试对话框 */
.dark-mode .test-dialog .el-dialog {
  background: #2c2c2c;
}

.dark-mode .test-dialog .el-dialog__header {
  background: #2c2c2c;
  border-bottom: 1px solid #404040;
}

.dark-mode .test-dialog .el-dialog__title,
.dark-mode .dialog-title-custom .title-text {
  color: #e4e4e4;
}

.dark-mode .test-dialog .el-dialog__close {
  color: #b0b0b0;
}

.dark-mode .test-dialog .el-dialog__close:hover {
  color: #e4e4e4;
}

.dark-mode .test-dialog .el-dialog__body {
  background: #2c2c2c;
  color: #e4e4e4;
}

.dark-mode .dialog-content {
  background: #2c2c2c;
}

/* 暗色模式滚动条样式已移至非scoped样式区域 */

/* 摘要信息区域暗色模式样式已移除（已移至标题区域） */

/* 输入区域标签 - 暗色模式 */
.dark-mode .input-section label,
.dark-mode .stream-mode label,
.dark-mode .result-section label,
.dark-mode .error-section label {
  color: #b0b0b0;
}

/* 输入框 - 暗色模式 */
.dark-mode .source-input .el-textarea__inner {
  background: #383838;
  border-color: #4F4F4F;
  color: #e4e4e4;
}

.dark-mode .source-input .el-textarea__inner:focus {
  background: #383838;
  border-color: #606266;
  color: #e4e4e4;
}

/* 结果输出 - 暗色模式 */
.dark-mode .result-output .el-textarea__inner {
  background: #383838;
  border-color: #4F4F4F;
  color: #e4e4e4;
}

/* 流式模式选择 - 暗色模式 */
.dark-mode .stream-radio .el-radio__label {
  color: #b0b0b0;
}

.dark-mode .stream-radio .el-radio__input.is-checked + .el-radio__label {
  color: #e4e4e4;
}

/* 输入提示 - 暗色模式 */
.dark-mode .input-tips {
  color: #b0b0b0;
}

.dark-mode .lang-item label {
  color: #b0b0b0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 10px;
  }
  
  /* 表单标签宽度适配 */
  .config-form {
    padding: 0 !important;
  }
  
  .config-form .el-form-item__label {
    width: 100px !important;
    font-size: 13px !important;
    padding-right: 8px !important;
  }
  
  .config-form .el-form-item__content {
    margin-left: 100px !important;
  }
  
  .title-section {
    padding: 15px;
  }
  
  .section-content {
  }
  
  /* 输入框和选择框全宽 */
  .full-width,
  .language-select,
  .number-input,
  .timeout-input {
    width: 100% !important;
    max-width: 100% !important;
  }
  
  /* 语言信息移动端适配已移除（已移至标题区域） */
  
  /* 操作按钮区域适配 */
  .action-bar {
    padding: 15px 0;
    flex-direction: column;
    gap: 10px;
  }
  
  .action-btn {
    width: 100% !important;
    min-width: unset !important;
  }
  
  /* 信息面板适配 */
  .info-panel {
    padding: 12px;
    margin-top: 12px;
  }
  
  .info-header {
    font-size: 13px;
  }
  
  .info-item {
    font-size: 12px;
  }
  
  /* 提示文本适配 */
  .form-tip {
    font-size: 11px;
    padding: 6px 10px;
  }
  
  /* 测试对话框适配 */
  
  .test-form {
    gap: 15px;
    padding: 15px;
  }
  
  /* 翻译按钮适配 */
  .translate-btn,
  .test-btn {
    width: 100% !important;
    padding: 12px !important;
  }
  
  /* 结果元数据适配 */
  .result-meta {
    flex-wrap: wrap;
    gap: 6px;
  }
  
  .result-meta .el-tag {
    font-size: 11px;
  }
}

/* 覆盖Element UI样式 */
.el-form-item__label {
  padding-bottom: 6px !important;
}

.el-select .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-select .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-input-number .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-input-number .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-radio__input.is-checked .el-radio__inner {
  border-color: #2d3748 !important;
  background: #2d3748 !important;
}

.el-radio__input.is-checked + .el-radio__label {
  color: #2d3748 !important;
}

/* 聊天测试弹窗相关样式 */
/* 注意：这个全局样式会影响所有对话框，test-dialog 的样式在非scoped区域覆盖 */

.test-form .el-form-item {
  margin: 10px;
  margin-bottom: 16px;
}

.test-form .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.test-translation-content {
  max-height: 300px;
  overflow-y: auto;
}

.test-translation-result {
  background-color: #f8f9fa;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  min-height: 60px;
  color: #2d3748;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.test-translation-result.empty {
  color: #a0aec0;
  font-style: italic;
}

.translation-meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #718096;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 16px;
  }
  

  
  .form-actions {
    flex-direction: column;
  }
  
  .action-button {
    margin: 4px 0;
  }
}

/* 语言配置相关样式 */
.language-config-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}

.language-item {
  flex: 1;
  min-width: 200px;
}

.language-item .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.language-select {
  width: 100%;
  max-width: 200px;
}

.language-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10px;
  color: #606266;
  font-size: 16px;
  min-width: 30px;
}

.language-arrow i {
  font-weight: bold;
  transform: scaleX(1.2);
}

/* 响应式语言配置 */
@media (max-width: 768px) {
  .language-config-row {
    flex-direction: column;
    gap: 16px;
  }
  
  .language-arrow {
    padding-top: 0;
    transform: rotate(90deg);
    height: 20px;
  }
  
  .language-item {
    min-width: 280px;
    max-width: 100%;
  }
}

/* 超小屏幕适配 */
@media (max-width: 480px) {
  .translation-management {
    padding: 8px;
  }
  
  /* 表单标签进一步缩小 */
  .config-form .el-form-item__label {
    width: 85px !important;
    font-size: 12px !important;
    padding-right: 6px !important;
    line-height: 1.3 !important;
    white-space: normal !important;
    word-break: break-all !important;
  }
  
  .config-form .el-form-item__content {
    margin-left: 85px !important;
  }
  
  /* 标签和卡片适配 */
  .my-tag {
    font-size: 14px !important;
    height: 36px !important;
    line-height: 36px !important;
    padding: 0 10px !important;
  }
  
  .my-tag svg {
    width: 16px !important;
    height: 16px !important;
  }
  
  /* 配置区块间距 */
  .config-section {
    margin-bottom: 15px;
  }
  
  .section-content {
  }
  
  /* 语言配置适配 */
  .language-item {
    min-width: unset;
    width: 100%;
  }
  
  .language-arrow {
    height: 16px;
    font-size: 14px;
  }
  
  /* 信息面板适配 */
  .info-panel {
    padding: 10px;
    margin-top: 10px;
  }
  
  .info-header {
    font-size: 12px;
    margin-bottom: 6px;
  }
  
  .info-item {
    font-size: 11px;
    line-height: 1.5;
  }
  
  /* 按钮适配 */
  .action-bar {
    padding: 12px 0;
    gap: 8px;
  }
  
  .action-btn {
    padding: 10px 15px !important;
    font-size: 13px !important;
  }
  
  /* 提示文本适配 */
  .form-tip {
    font-size: 10px;
    padding: 5px 8px;
    line-height: 1.4;
  }
  
  /* Alert 提示框适配 */
  .source-lang-warning .el-alert,
  .source-lang-info .el-alert {
    padding: 10px 12px;
  }
  
  .source-lang-warning .el-alert__title,
  .source-lang-info .el-alert__title {
    font-size: 12px;
  }
  
  .source-lang-warning .el-alert__content {
    font-size: 11px;
    line-height: 1.5;
  }
  
  /* 测试对话框适配 */
  
  .test-form {
    gap: 12px;
    padding: 12px;
  }
  
  /* 对话框输入区域 */
  .input-section label,
  .stream-mode label,
  .result-section label {
    font-size: 13px;
  }
  
  .source-input .el-textarea__inner,
  .result-output .el-textarea__inner {
    font-size: 13px;
    padding: 10px;
  }
  
  /* 流式模式选择适配 */
  .stream-options {
    flex-direction: column;
    gap: 10px;
  }
  
  .stream-radio {
    margin-right: 0 !important;
  }
  
  /* 摘要配置移动端适配已移除（已移至标题区域） */
  
  .input-tips {
    font-size: 11px;
    margin-top: 6px;
  }
  
  /* 对话框底部按钮 */
  .dialog-footer {
    padding: 12px;
    display: flex;
    gap: 8px;
  }
  
  .dialog-footer .el-button {
    flex: 1;
    padding: 10px 12px;
    font-size: 13px;
  }
}

/* 本地摘录测试对话框语言信息样式已移除（已移至标题区域） */

.dual-input-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 20px;
}

.dual-input-section .input-column {
  display: flex;
  flex-direction: column;
}

.dual-input-section .input-column label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 10px;
}

.dual-input-section .char-count {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
  text-align: right;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .dual-input-section {
    grid-template-columns: 1fr;
    gap: 15px;
  }
}

/* 统一页面标题样式 */
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

/* 源语言状态提示样式 */
.source-lang-warning,
.source-lang-info {
  margin-top: 12px;
}

.source-lang-warning .el-alert,
.source-lang-info .el-alert {
  border-radius: 4px;
  padding: 12px 16px;
}

.source-lang-warning .el-alert__title,
.source-lang-info .el-alert__title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}

.source-lang-info .el-alert__content {
  font-size: 12px;
  color: #67C23A;
}

.source-lang-warning .el-alert__content {
  font-size: 12px;
  line-height: 1.4;
  margin-top: 4px;
}

/* 禁用状态的选择器样式 */
.language-select.is-disabled .el-input__inner {
  background-color: #f5f7fa !important;
  border-color: #e4e7ed !important;
  color: #c0c4cc !important;
  cursor: not-allowed !important;
}

.language-select.is-disabled .el-input__suffix {
  color: #c0c4cc !important;
}

/* ===========================================
   表单移动端样式 - PC端和移动端响应式
   =========================================== */

/* PC端样式 - 768px以上 */
@media screen and (min-width: 769px) {
  ::v-deep .el-form-item__label {
    float: left !important;
  }
}

/* 移动端样式 - 768px及以下 */
@media screen and (max-width: 768px) {
  /* 表单标签 - 垂直布局 */
  ::v-deep .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    margin-bottom: 8px !important;
    font-weight: 500 !important;
    font-size: 14px !important;
    padding-bottom: 0 !important;
    line-height: 1.5 !important;
  }

  ::v-deep .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  ::v-deep .el-form-item {
    margin-bottom: 20px !important;
  }

  /* 输入框移动端优化 */
  ::v-deep .el-input__inner {
    font-size: 16px !important;
    height: 44px !important;
    border-radius: 8px !important;
  }

  ::v-deep .el-textarea__inner {
    font-size: 16px !important;
    border-radius: 8px !important;
  }

  /* 选择器移动端优化 */
  ::v-deep .el-select {
    width: 100% !important;
  }

  ::v-deep .el-select .el-input__inner {
    height: 44px !important;
    line-height: 44px !important;
  }

  /* 数字输入框移动端优化 */
  ::v-deep .el-input-number {
    width: 100% !important;
  }

  .number-input {
    width: 100% !important;
  }

  /* 按钮移动端优化 */
  ::v-deep .el-button {
    min-height: 40px !important;
    border-radius: 8px !important;
  }

  /* 对话框移动端优化 */
  ::v-deep .el-dialog {
    width: 95% !important;
    margin-top: 5vh !important;
  }

  ::v-deep .el-dialog__body {
    padding: 15px !important;
  }

  /* 页面容器移动端优化 */
  .translation-management {
    padding: 0 10px !important;
  }

  .section-header {
    padding: 12px 16px !important;
  }

  .section-title {
    font-size: 15px !important;
  }
}

/* 极小屏幕优化 - 480px及以下 */
@media screen and (max-width: 480px) {
  ::v-deep .el-form-item__label {
    font-size: 13px !important;
  }

  ::v-deep .el-input__inner,
  ::v-deep .el-select .el-input__inner {
    height: 40px !important;
    line-height: 40px !important;
    font-size: 15px !important;
  }

  ::v-deep .el-button {
    min-height: 38px !important;
    font-size: 14px !important;
  }

  .section-header {
    padding: 10px 12px !important;
  }

  .section-title {
    font-size: 14px !important;
  }
}
</style>

<style>

</style>
