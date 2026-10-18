<template>
  <el-drawer
    v-model="visible"
    :title="`合同履约全息视窗：${contract?.contractCode || ''}`"
    size="840px"
    destroy-on-close
  >
    <div v-loading="loading" class="contract-drawer-content">
      <!-- 顶部工程铭牌与四率看板 -->
      <div class="summary-card mb-4">
        <div class="top-row">
          <div class="title-block">
            <span class="main-title font-semibold">{{ contract?.partyAName ? `${contract.partyAName} - 施工合同` : '施工合同' }}</span>
            <span class="sub-code font-mono ml-2">{{ contract?.contractCode }}</span>
            <el-tag :type="statusTagType(contract?.status)" size="small" class="ml-2">
              {{ statusLabelMap[contract?.status] || contract?.status }}
            </el-tag>
          </div>
          <div class="meta-block text-xs text-secondary">
            <span>项目：{{ contract?.projectName || '-' }}</span>
            <span class="ml-3 font-mono">签订：{{ contract?.signingDate || '-' }}</span>
          </div>
        </div>

        <el-divider class="my-2" />

        <!-- 四大核心金额指标 -->
        <div class="kpi-grid">
          <div class="kpi-item">
            <span class="kpi-label">合同总金额</span>
            <span class="kpi-value text-brand font-semibold">
              ¥ {{ Number(contract?.contractAmount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">不含税金额 / 税率</span>
            <span class="kpi-value">
              ¥ {{ Number(contract?.amountWithoutTax || 0).toLocaleString() }}
              <span class="text-xs text-secondary">({{ contract?.taxRate || 0 }}%)</span>
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">累计变更金额</span>
            <span class="kpi-value" :class="contract?.cumulativeChangeAmount > 0 ? 'text-warning font-medium' : ''">
              ¥ {{ Number(contract?.cumulativeChangeAmount || 0).toLocaleString() }}
            </span>
          </div>
          <div class="kpi-item">
            <span class="kpi-label">调整后总额</span>
            <span class="kpi-value font-semibold">
              ¥ {{ (Number(contract?.contractAmount || 0) + Number(contract?.cumulativeChangeAmount || 0)).toLocaleString() }}
            </span>
          </div>
        </div>

        <!-- 履约四率进度条 (产值率 / 开票率 / 回款率) -->
        <div class="progress-section mt-3">
          <p v-if="!Number(contract?.contractAmount)">履约率：—（合同金额为零或未知，比例不适用）</p>
          <div class="progress-row">
            <span class="progress-label">产值率 (¥ {{ Number(contract?.cumulativeOutput || 0).toLocaleString() }})</span>
            <el-progress
              v-if="calcRate(contract?.cumulativeOutput, contract?.contractAmount) !== null"
              :percentage="calcRate(contract?.cumulativeOutput, contract?.contractAmount)!"
              status="success"
              class="flex-1 ml-2"
            />
          </div>
          <div class="progress-row mt-2">
            <span class="progress-label">开票率 (¥ {{ Number(contract?.cumulativeInvoiceAmount || 0).toLocaleString() }})</span>
            <el-progress
              v-if="calcRate(contract?.cumulativeInvoiceAmount, contract?.contractAmount) !== null"
              :percentage="calcRate(contract?.cumulativeInvoiceAmount, contract?.contractAmount)!"
              class="flex-1 ml-2"
            />
          </div>
          <div class="progress-row mt-2">
            <span class="progress-label">回款率 (¥ {{ Number(contract?.cumulativeReceivedAmount || 0).toLocaleString() }})</span>
            <el-progress
              v-if="calcRate(contract?.cumulativeReceivedAmount, contract?.contractAmount) !== null"
              :percentage="calcRate(contract?.cumulativeReceivedAmount, contract?.contractAmount)!"
              status="warning"
              class="flex-1 ml-2"
            />
          </div>
        </div>
      </div>

      <!-- Tab 页签 -->
      <section aria-label="盖章附件">
        <p v-if="sealError" role="alert">{{ sealError }} <el-button @click="loadSealedFiles">重试</el-button></p>
        <el-tag v-else>{{ sealedFiles.length ? '已上传盖章件' : '未上传盖章件' }}</el-tag>
        <el-upload :auto-upload="false" :show-file-list="false" :on-change="uploadSeal" :disabled="sealUploading"><el-button :loading="sealUploading">上传盖章件</el-button></el-upload>
        <ul><li v-for="file in sealedFiles" :key="file.id">{{ file.originalName || file.fileName }}</li></ul>
      </section>
      <el-tabs v-model="activeTab" class="contract-tabs">
        <!-- Tab 1: 基本条款与清单明细 -->
        <el-tab-pane label="基本要素与清单明细" name="details">
          <el-descriptions :column="2" border class="mb-3">
            <el-descriptions-item label="甲方单位">{{ contract?.partyAName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合同性质">{{ contract?.contractType === 'REGISTER' ? '登记合同' : contract?.contractType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="计划工期">
              {{ contract?.startDate || '-' }} 至 {{ contract?.endDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="税额 (¥)">
              ¥ {{ Number(contract?.taxAmount || 0).toLocaleString() }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-header mb-2 font-medium">合同清单明细列表 (共 {{ details.length }} 项)</div>
          <el-table :data="details" border size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="itemName" label="清单项目名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="specification" label="规格型号" width="120" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="quantity" label="工程量" width="100" align="right">
              <template #default="{ row }">{{ Number(row.quantity || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="综合单价(元)" width="110" align="right">
              <template #default="{ row }">¥ {{ Number(row.unitPrice || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="totalPrice" label="合价(元)" width="120" align="right">
              <template #default="{ row }">¥ {{ Number(row.totalPrice || 0).toLocaleString() }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 变更历程 (变更签证 + 变更事件) -->
        <el-tab-pane label="变更历程与签证" name="changes">
          <div class="tab-toolbar mb-3">
            <span class="font-medium">变更签证与项目变更记录</span>
            <el-button
              v-if="contract?.status === 'EFFECTIVE'"
              type="primary"
              size="small"
              @click="handleAddVisa"
            >
              登记变更签证
            </el-button>
          </div>

          <el-table :data="changeVisas" border size="small" class="mb-4">
            <el-table-column prop="changeType" label="签证类型" width="120">
              <template #default="{ row }">
                {{ row.changeType === 'DESIGN_CHANGE' ? '设计变更' : row.changeType === 'SITE_VISA' ? '现场签证' : row.changeType }}
              </template>
            </el-table-column>
            <el-table-column prop="visaDate" label="签证日期" width="120" />
            <el-table-column prop="taxAmount" label="税额" width="120" />
            <el-table-column prop="changeAmount" label="增减金额(元)" width="130" align="right">
              <template #default="{ row }">
                <span :class="Number(row.changeAmount) >= 0 ? 'text-success' : 'text-danger'">
                  {{ Number(row.changeAmount) >= 0 ? '+' : '' }}¥ {{ Number(row.changeAmount || 0).toLocaleString() }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="changeReason" label="变更原因" min-width="150" show-overflow-tooltip />
            <el-table-column prop="changeContent" label="变更内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="visaStatusType(row.status)" size="small">
                  {{ visaStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
                  link
                  type="success"
                  @click="handleSubmitVisa(row)"
                >
                  提交审批
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <ZwEmptyState v-if="!changeVisas.length" description="暂无变更签证记录" />
        </el-tab-pane>

        <!-- Tab 3: 产值执行台账 -->
        <el-tab-pane label="产值上报记录" name="outputs">
          <div class="section-header mb-2 font-medium">累计产值上报明细</div>
          <el-table :data="outputReports" border size="small">
            <el-table-column prop="reportPeriod" label="报告期间" width="110" />
            <el-table-column prop="currentOutput" label="本期完成产值(元)" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.currentOutput || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="cumulativeOutput" label="累计产值(元)" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.cumulativeOutput || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'APPROVED' ? '已确认' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="150" show-overflow-tooltip />
          </el-table>
          <ZwEmptyState v-if="!outputReports.length" description="暂无产值上报记录" />
        </el-tab-pane>

        <!-- Tab 4: 收付款条款（V2026_109） -->
        <el-tab-pane name="payTerms">
          <template #label>收付款条款</template>
          <div class="section-header mb-2 font-medium flex items-center justify-between">
            <span>价款支付约定（预付款/进度款/竣工款/质保金）</span>
            <el-button size="small" type="primary" @click="openTermDialog()">新增条款</el-button>
          </div>
          <el-table :data="payTerms" border size="small">
            <el-table-column prop="termType" label="类型" width="90">
              <template #default="{ row }">{{ termTypeLabel(row.termType) }}</template>
            </el-table-column>
            <el-table-column label="比例/金额" width="150" align="right">
              <template #default="{ row }">
                {{ row.ratio != null ? row.ratio + '%' : '' }}{{ row.ratio != null && row.amount != null ? ' / ' : '' }}{{ row.amount != null ? '¥' + Number(row.amount).toLocaleString() : '' }}
              </template>
            </el-table-column>
            <el-table-column prop="triggerCondition" label="触发条件" min-width="160" show-overflow-tooltip />
            <el-table-column prop="planDate" label="计划日期" width="100" />
            <el-table-column label="质保期/扣回" width="130">
              <template #default="{ row }">
                {{ row.retentionMonths ? row.retentionMonths + '个月' : '' }}{{ row.deductRule ? ' / ' + row.deductRule : '' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="openTermDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="removeTerm(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <ZwEmptyState v-if="!payTerms.length" description="暂无收付款条款；过程结算的质保金计提与预付款扣回建议将引用此处条款" />
        </el-tab-pane>

        <!-- Tab 5: 过程结算（V2026_108，财建〔2022〕183 号过程结算） -->
        <el-tab-pane name="processSettlement">
          <template #label>
            过程结算
            <el-tag v-if="processSettlements.length" size="small" type="warning" class="ml-1">{{ processSettlements.length }}</el-tag>
          </template>
          <div class="section-header mb-2 font-medium flex items-center justify-between">
            <span>过程结算单（按节点确认无争议价款）</span>
            <el-button size="small" type="primary" @click="openProcessSettlementDialog">新建过程结算</el-button>
          </div>
          <el-table :data="processSettlements" border size="small">
            <el-table-column prop="periodStart" label="期间起" width="100" />
            <el-table-column prop="periodEnd" label="期间止" width="100" />
            <el-table-column prop="declaredAmount" label="申报合计(元)" width="120" align="right">
              <template #default="{ row }">¥ {{ Number(row.declaredAmount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="settlementAmount" label="审定净额(元)" width="120" align="right">
              <template #default="{ row }">¥ {{ Number(row.settlementAmount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="扣款(预付/质保/其他)" width="150" align="right">
              <template #default="{ row }">
                ¥ {{ Number(row.prepayDeduct || 0).toLocaleString() }} / {{ Number(row.retentionDeduct || 0).toLocaleString() }} / {{ Number(row.otherDeduct || 0).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column prop="disputedAmount" label="争议(元)" width="100" align="right">
              <template #default="{ row }">¥ {{ Number(row.disputedAmount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'APPROVED' ? '已生效' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" link @click="submitProcessSettlement(row)">提交审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <ZwEmptyState v-if="!processSettlements.length" description="暂无过程结算，按月/节点新建以确认本期无争议价款" />
          <div class="mt-2 text-xs text-gray-400">
            审定净额 = Σ来源审定 − Σ扣款；生效后原子回写合同累计结算并按期间末+账期生成应收。
          </div>
        </el-tab-pane>

        <!-- Tab 5: 竣工结算单 -->
        <el-tab-pane label="竣工结算" name="settlement">
          <div v-if="finalSettlement" class="settlement-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="最终审定结算价">
                <span class="text-brand font-semibold">¥ {{ Number(finalSettlement.settlementAmount || 0).toLocaleString() }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="审减/增额">
                ¥ {{ Number(finalSettlement.auditDifference || 0).toLocaleString() }}
              </el-descriptions-item>
              <el-descriptions-item label="结算状态">
                <el-tag type="success">已生效结算</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="结算说明" :span="2">
                {{ finalSettlement.remark || '无' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <ZwEmptyState v-else description="尚未发起竣工结算" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 弹窗：登记变更签证 (B1) -->
    <el-dialog v-model="visaDialogVisible" title="登记变更签证" width="500px" append-to-body>
      <el-form ref="visaFormRef" :model="visaForm" :rules="visaRules" label-width="110px">
        <el-form-item label="关联变更事件">
          <el-select v-model="visaForm.changeEventId" clearable placeholder="可选：事件为事实，签证为价款确认（防双计）" style="width: 100%" :loading="visaEventsLoading">
            <el-option v-for="ev in visaEventOptions" :key="ev.id" :label="ev.label" :value="ev.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更类型" prop="changeType">
          <el-select v-model="visaForm.changeType" placeholder="选择类型" style="width: 100%">
            <el-option label="设计变更" value="DESIGN_CHANGE" />
            <el-option label="现场签证" value="SITE_VISA" />
            <el-option label="技术核定" value="TECH_CONFIRM" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更金额" prop="changeAmount">
          <el-input-number
            v-model="visaForm.changeAmount"
            :precision="2"
            :step="1000"
            controls-position="right"
            style="width: 100%"
            placeholder="正数调增，负数调减"
          />
        </el-form-item>
        <el-form-item label="签证日期" prop="visaDate"><el-date-picker v-model="visaForm.visaDate" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="税率(%)"><el-input-number v-model="visaForm.taxRate" :min="0" :max="100" :precision="2" /></el-form-item>
        <el-form-item label="变更原因" prop="changeReason">
          <el-input v-model="visaForm.changeReason" placeholder="如：业主设计调整、地质变动" />
        </el-form-item>
        <el-form-item label="变更内容" prop="changeContent">
          <el-input v-model="visaForm.changeContent" type="textarea" :rows="3" placeholder="详细变更部位与技术核定说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="visaSubmitting" @click="submitVisa">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：新建过程结算（V2026_108） -->
    <el-dialog v-model="psDialogVisible" title="新建过程结算" width="860px" append-to-body>
      <el-form ref="psFormRef" :model="psForm" :rules="psRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="结算期间起" prop="periodStart">
              <el-date-picker v-model="psForm.periodStart" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结算期间止" prop="periodEnd">
              <el-date-picker v-model="psForm.periodEnd" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结算日期">
              <el-date-picker v-model="psForm.settlementDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-header mb-2 font-medium">来源明细（已批产值 / 已批签证 / 手工行）</div>
        <el-table :data="psForm.sources" border size="small">
          <el-table-column label="类型" width="120">
            <template #default="{ row, $index }">
              <el-select v-model="row.sourceType" size="small" @change="onSourceTypeChange()">
                <el-option label="产值报告" value="OUTPUT_REPORT" :disabled="!psAvailable.outputReports.length" />
                <el-option label="变更签证" value="VISA" :disabled="!psAvailable.visas.length" />
                <el-option label="手工行" value="MANUAL" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="来源/描述" min-width="200">
            <template #default="{ row }">
              <el-select v-if="row.sourceType !== 'MANUAL'" v-model="row.sourceId" size="small" placeholder="选择来源" style="width: 100%">
                <el-option v-for="s in psSourceOptions(row.sourceType)" :key="s.sourceId" :label="s.label" :value="s.sourceId" />
              </el-select>
              <el-input v-else v-model="row.description" size="small" placeholder="索赔/奖罚/其他事项描述" />
            </template>
          </el-table-column>
          <el-table-column label="申报" width="130" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.declaredAmount" :precision="2" :min="0" size="small" controls-position="right" style="width: 120px" @change="prefillConfirmed(row)" />
            </template>
          </el-table-column>
          <el-table-column label="审定" width="130" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.confirmedAmount" :precision="2" :min="0" size="small" controls-position="right" style="width: 120px" />
            </template>
          </el-table-column>
          <el-table-column label="争议" width="130" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.disputedAmount" :precision="2" :min="0" size="small" controls-position="right" style="width: 120px" />
            </template>
          </el-table-column>
          <el-table-column label="核减原因" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.reduceReason" size="small" placeholder="审定<申报时必填" :disabled="Number(row.confirmedAmount || 0) >= Number(row.declaredAmount || 0)" />
            </template>
          </el-table-column>
          <el-table-column width="60" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="psForm.sources.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" class="mt-1" @click="addPsSource">+ 添加来源</el-button>

        <el-row :gutter="12" class="mt-2">
          <el-col :span="8">
            <el-form-item label="预付款扣回"><el-input-number v-model="psForm.prepayDeduct" :precision="2" :min="0" size="small" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="质保金扣留"><el-input-number v-model="psForm.retentionDeduct" :precision="2" :min="0" size="small" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="其他扣款"><el-input-number v-model="psForm.otherDeduct" :precision="2" :min="0" size="small" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <div class="text-xs text-gray-400">
          审定净额（服务端重算）≈ ¥ {{ psNetPreview.toLocaleString() }}；超合同额（含变更）会被后端驳回。
          <template v-if="payTerms.find((t: any) => t.termType === 'RETENTION')">
            合同约定质保金 {{ payTerms.find((t: any) => t.termType === 'RETENTION')?.ratio ?? '—' }}%{{ payTerms.find((t: any) => t.termType === 'RETENTION')?.retentionMonths ? `，质保期 ${payTerms.find((t: any) => t.termType === 'RETENTION').retentionMonths} 个月` : '' }}，请按审定额扣留；生效后自动计提台账。
          </template>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="psDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="psSubmitting" @click="submitProcessSettlementCreate">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：收付款条款（V2026_109） -->
    <el-dialog v-model="termDialogVisible" :title="termForm.id ? '编辑收付款条款' : '新增收付款条款'" width="560px" append-to-body>
      <el-form ref="termFormRef" :model="termForm" :rules="termRules" label-width="110px">
        <el-form-item label="条款类型" prop="termType">
          <el-select v-model="termForm.termType" style="width: 100%">
            <el-option label="预付款" value="PREPAY" />
            <el-option label="进度款" value="PROGRESS" />
            <el-option label="竣工结算款" value="FINAL" />
            <el-option label="质保金" value="RETENTION" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="比例(%)">
              <el-input-number v-model="termForm.ratio" :min="0" :max="100" :precision="2" style="width: 100%" :disabled="termForm.termType === 'RETENTION' && (termForm.ratio ?? 0) > 3" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额(元)">
              <el-input-number v-model="termForm.amount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发条件">
          <el-input v-model="termForm.triggerCondition" placeholder="如：进场且报验合格后支付；并网后付至80%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划日期"><el-date-picker v-model="termForm.planDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="termForm.termType === 'RETENTION'" label="质保期(月)">
              <el-input-number v-model="termForm.retentionMonths" :min="0" :max="120" style="width: 100%" />
            </el-form-item>
            <el-form-item v-else-if="termForm.termType === 'PREPAY'" label="扣回规则">
              <el-select v-model="termForm.deductRule" clearable style="width: 100%">
                <el-option label="按期均扣" value="PER_STAGE" />
                <el-option label="付至80%后扣回" value="AFTER_80" />
                <el-option label="不自动扣回" value="NONE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="termForm.termType === 'RETENTION'" class="text-xs text-gray-400">
          质量保证金比例不得超过工程价款总额的 3%（财建〔2022〕183号）。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="termSubmitting" @click="submitTerm">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import { CONTRACT_STATUS } from '@/constants/biz-labels'
import { getContractSealedFiles, uploadContractSealedFile } from '@/api/contract'
const sealedFiles = ref<any[]>([])
const sealError = ref('')
const sealUploading = ref(false)
async function loadSealedFiles() {
  sealedFiles.value = []; sealError.value = ''
  try {
    const res = await getContractSealedFiles(props.contract.id)
    if (!Array.isArray(res.data)) throw new Error('invalid files')
    sealedFiles.value = res.data
  } catch { sealError.value = '盖章附件状态未知，加载失败，请重试' }
}
async function uploadSeal(file: any) {
  if (!file.raw || sealUploading.value) return
  sealUploading.value = true
  try {
    await uploadContractSealedFile(props.contract.id, file.raw)
    await loadSealedFiles()
  } catch { ElMessage.error('盖章附件上传失败') }
  finally { sealUploading.value = false }
}
import {
  getContractDetails,
  getChangeVisaPage,
  createChangeVisa,
  submitChangeVisa,
  getOutputReportPage,
  getFinalSettlementPage,
  createFinalSettlement,
  submitFinalSettlement,
  getAvailableSettlementSources,
  getPayTerms,
  createPayTerm,
  updatePayTerm,
  deletePayTerm
} from '@/api/contract'
import { listChangeEvents } from '@/api/change-event'

const props = defineProps<{
  modelValue: boolean
  contract?: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const activeTab = ref('details')

// 状态显示名唯一源：@/constants/biz-labels（术语表 §四；CLOSED 统一“已结项”）
const statusLabelMap: Record<string, string> = Object.fromEntries(
  Object.entries(CONTRACT_STATUS).map(([k, v]) => [k, v.label])
)

function statusTagType(status?: string) {
  if (status === 'EFFECTIVE') return 'success'
  if (status === 'SUBMITTED') return 'warning'
  if (status === 'SETTLED') return 'primary'
  if (status === 'CLOSED') return 'info'
  return 'info'
}

function calcRate(amount?: number, total?: number): number | null {
  if (total == null || Number(total) === 0) return null
  if (amount == null) return null
  const rate = Math.round((Number(amount) / Number(total)) * 100)
  return Math.min(rate, 100)
}

// 变更签证状态展示（后端 save 恒置 DRAFT，submit 后 SUBMITTED，审批回调 APPROVED/REJECTED）
const visaStatusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '编制中', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已批准', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

function visaStatusLabel(status: string) {
  return visaStatusMap[status]?.label || status || '编制中'
}

function visaStatusType(status: string) {
  return (visaStatusMap[status]?.type || 'info') as any
}

const details = ref<any[]>([])
const changeVisas = ref<any[]>([])
const outputReports = ref<any[]>([])
const finalSettlement = ref<any>(null)
const processSettlements = ref<any[]>([])

// 变更签证弹窗
const visaDialogVisible = ref(false)
const visaSubmitting = ref(false)
const visaFormRef = ref<FormInstance>()
const visaEventOptions = ref<Array<{ id: number; label: string }>>([])
const visaEventsLoading = ref(false)
const visaForm = ref({
  changeType: 'DESIGN_CHANGE',
  changeAmount: undefined as number | undefined,
  visaDate: '',
  taxRate: undefined as number | undefined,
  changeReason: '',
  changeContent: '',
  changeEventId: undefined as number | undefined
})
const visaRules = {
  visaDate: [{ required: true, message: '请选择签证日期', trigger: 'change' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  changeAmount: [{ required: true, message: '请输入变更金额', trigger: 'blur' }],
  changeReason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }]
}

watch(
  () => [props.modelValue, props.contract],
  async ([val]) => {
    if (val && props.contract?.id) {
      activeTab.value = 'details'
      await loadAllData()
    }
  },
  { immediate: true }
)

async function loadAllData() {
  if (!props.contract?.id) return
  loading.value = true
  const cid = props.contract.id
  await loadSealedFiles()
  try {
    // 1. 合同明细
    try {
      const dRes: any = await getContractDetails(cid)
      details.value = dRes.data || []
    } catch {
      details.value = []
    }

    // 2. 变更签证
    await loadChangeVisas()

    // 3. 产值记录
    try {
      const oRes: any = await getOutputReportPage({ contractId: cid, pageNum: 1, pageSize: 50 })
      outputReports.value = oRes.data?.records || []
    } catch {
      outputReports.value = []
    }

    // 4. 竣工结算（仅 FINAL 型；PROCESS 型在过程结算页签展示）
    try {
      const sRes: any = await getFinalSettlementPage({ contractId: cid, settlementType: 'FINAL', pageNum: 1, pageSize: 1 })
      finalSettlement.value = sRes.data?.records?.[0] || null
    } catch {
      finalSettlement.value = null
    }

    // 5. 过程结算（V2026_108）
    await loadProcessSettlements()

    // 6. 收付款条款（V2026_109）
    await loadPayTerms()
  } finally {
    loading.value = false
  }
}

async function loadChangeVisas() {
  if (!props.contract?.id) return
  try {
    const vRes: any = await getChangeVisaPage({ contractId: props.contract.id, pageNum: 1, pageSize: 50 })
    changeVisas.value = vRes.data?.records || []
  } catch {
    changeVisas.value = []
  }
}

function handleAddVisa() {
  visaForm.value = {
    changeType: 'DESIGN_CHANGE',
    changeAmount: undefined,
    visaDate: '',
    taxRate: undefined,
    changeReason: '',
    changeContent: '',
    changeEventId: undefined
  }
  visaDialogVisible.value = true
  loadVisaEventOptions()
}

/** 签证可选关联的已审批变更事件（单一生效源：事件=事实，签证=价款确认） */
async function loadVisaEventOptions() {
  visaEventsLoading.value = true
  try {
    const res: any = await listChangeEvents({
      page: 1,
      size: 50,
      projectId: props.contract?.projectId,
      status: 'APPROVED'
    })
    const records = res.data?.records || []
    visaEventOptions.value = records
      .filter((e: any) => e.contractId == null || Number(e.contractId) === Number(props.contract.id))
      .map((e: any) => ({
        id: Number(e.id),
        label: `${e.eventNumber || e.id} ${e.title || ''}（成本影响 ${Number(e.costDelta || 0).toLocaleString()}）`
      }))
  } catch {
    visaEventOptions.value = []
  } finally {
    visaEventsLoading.value = false
  }
}

async function submitVisa() {
  await visaFormRef.value?.validate()
  visaSubmitting.value = true
  try {
    // 后端 ChangeVisaService.save 硬编码 status=DRAFT（提交审批须经 submit 接口），
    // 前端不再传 status（D 清理：原显式传 'APPROVED' 无意义且误导）
    await createChangeVisa({
      projectId: props.contract.projectId,
      contractId: props.contract.id,
      changeType: visaForm.value.changeType,
      changeAmount: visaForm.value.changeAmount,
      visaDate: visaForm.value.visaDate,
      taxRate: visaForm.value.taxRate,
      changeReason: visaForm.value.changeReason,
      changeContent: visaForm.value.changeContent,
      changeEventId: visaForm.value.changeEventId
    })
    ElMessage.success('变更签证登记成功')
    visaDialogVisible.value = false
    await loadChangeVisas()
    emit('refresh')
  } finally {
    visaSubmitting.value = false
  }
}

async function handleSubmitVisa(row: any) {
  try {
    await ElMessageBox.confirm('确定提交该变更签证进入审批流程吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await submitChangeVisa(row.id)
  ElMessage.success('已提交审批，审批通过后生效')
  await loadChangeVisas()
}

// ==================== 过程结算（V2026_108） ====================

const psDialogVisible = ref(false)
const psSubmitting = ref(false)
const psFormRef = ref<FormInstance>()
const psAvailable = ref<{ outputReports: any[]; visas: any[] }>({ outputReports: [], visas: [] })
const psForm = ref({
  periodStart: '',
  periodEnd: '',
  settlementDate: '',
  prepayDeduct: 0,
  retentionDeduct: 0,
  otherDeduct: 0,
  sources: [] as Array<{
    sourceType: 'OUTPUT_REPORT' | 'VISA' | 'MANUAL'
    sourceId?: number
    description?: string
    declaredAmount: number
    confirmedAmount: number
    disputedAmount: number
    reduceReason?: string
  }>
})
const psRules = {
  periodStart: [{ required: true, message: '请选择期间起', trigger: 'change' }],
  periodEnd: [{ required: true, message: '请选择期间止', trigger: 'change' }]
}

const psNetPreview = computed(() => {
  const confirmed = psForm.value.sources.reduce((s, r) => s + Number(r.confirmedAmount || 0), 0)
  const deduct = Number(psForm.value.prepayDeduct || 0) + Number(psForm.value.retentionDeduct || 0) + Number(psForm.value.otherDeduct || 0)
  return confirmed - deduct
})

function psSourceOptions(type: string) {
  if (type === 'OUTPUT_REPORT') return psAvailable.value.outputReports
  if (type === 'VISA') return psAvailable.value.visas
  return []
}

function onSourceTypeChange() {
  // 切换类型后清空来源选择，保留金额
}

function prefillConfirmed(row: any) {
  if (row.confirmedAmount == null) row.confirmedAmount = row.declaredAmount
}

function addPsSource() {
  const hasReports = psAvailable.value.outputReports.length > 0
  psForm.value.sources.push({
    sourceType: hasReports ? 'OUTPUT_REPORT' : 'MANUAL',
    sourceId: undefined,
    description: '',
    declaredAmount: 0,
    confirmedAmount: 0,
    disputedAmount: 0,
    reduceReason: ''
  })
}

async function openProcessSettlementDialog() {
  psForm.value = {
    periodStart: '',
    periodEnd: '',
    settlementDate: '',
    prepayDeduct: 0,
    retentionDeduct: 0,
    otherDeduct: 0,
    sources: []
  }
  psDialogVisible.value = true
  try {
    const res: any = await getAvailableSettlementSources(props.contract.id)
    psAvailable.value = {
      outputReports: (res.data?.outputReports || []).map((r: any) => ({ ...r, sourceId: Number(r.sourceId) })),
      visas: (res.data?.visas || []).map((v: any) => ({ ...v, sourceId: Number(v.sourceId) }))
    }
  } catch {
    psAvailable.value = { outputReports: [], visas: [] }
    ElMessage.warning('可选来源加载失败，仍可手工行录入')
  }
  // V2026_109：按合同条款预填预付款扣回建议；质保金条款作提示（v1 人工确认）
  const prepayTerm = payTerms.value.find((t: any) => t.termType === 'PREPAY' && t.amount)
  if (prepayTerm) psForm.value.prepayDeduct = Number(prepayTerm.amount)
  if (!psForm.value.sources.length) addPsSource()
}

async function submitProcessSettlementCreate() {
  await psFormRef.value?.validate()
  if (!psForm.value.sources.length) {
    ElMessage.warning('至少一条来源明细')
    return
  }
  psSubmitting.value = true
  try {
    await createFinalSettlement({
      projectId: props.contract.projectId,
      contractId: props.contract.id,
      settlementType: 'PROCESS',
      periodStart: psForm.value.periodStart,
      periodEnd: psForm.value.periodEnd,
      settlementDate: psForm.value.settlementDate || undefined,
      prepayDeduct: psForm.value.prepayDeduct,
      retentionDeduct: psForm.value.retentionDeduct,
      otherDeduct: psForm.value.otherDeduct,
      sources: psForm.value.sources
    })
    ElMessage.success('过程结算草稿已保存，请核对后提交审批')
    psDialogVisible.value = false
    await loadProcessSettlements()
    emit('refresh')
  } finally {
    psSubmitting.value = false
  }
}

async function submitProcessSettlement(row: any) {
  try {
    await ElMessageBox.confirm('确定提交该过程结算进入审批流程吗？生效后将回写合同累计结算并生成应收。', '提示', { type: 'warning' })
  } catch {
    return
  }
  await submitFinalSettlement(row.id)
  ElMessage.success('已提交审批')
  await loadProcessSettlements()
}

async function loadProcessSettlements() {
  if (!props.contract?.id) return
  try {
    const res: any = await getFinalSettlementPage({
      contractId: props.contract.id,
      settlementType: 'PROCESS',
      pageNum: 1,
      pageSize: 50
    })
    processSettlements.value = res.data?.records || []
  } catch {
    processSettlements.value = []
  }
}

// ==================== 收付款条款（V2026_109） ====================

const payTerms = ref<any[]>([])
const termDialogVisible = ref(false)
const termSubmitting = ref(false)
const termFormRef = ref<FormInstance>()
const termForm = ref<any>({})
const termRules = {
  termType: [{ required: true, message: '请选择条款类型', trigger: 'change' }]
}

function termTypeLabel(type: string) {
  const map: Record<string, string> = { PREPAY: '预付款', PROGRESS: '进度款', FINAL: '竣工款', RETENTION: '质保金' }
  return map[type] || type
}

async function loadPayTerms() {
  if (!props.contract?.id) return
  try {
    const res: any = await getPayTerms('CONSTRUCTION', props.contract.id)
    payTerms.value = Array.isArray(res.data) ? res.data : []
  } catch {
    payTerms.value = []
  }
}

function openTermDialog(row?: any) {
  termForm.value = row
    ? { ...row }
    : { termType: 'PROGRESS', ratio: undefined, amount: undefined, triggerCondition: '', planDate: '', retentionMonths: undefined, deductRule: undefined }
  termDialogVisible.value = true
}

async function submitTerm() {
  await termFormRef.value?.validate()
  if (termForm.value.ratio == null && termForm.value.amount == null) {
    ElMessage.warning('比例与金额至少填一项')
    return
  }
  termSubmitting.value = true
  try {
    const payload = {
      contractCategory: 'CONSTRUCTION',
      contractId: props.contract.id,
      termType: termForm.value.termType,
      ratio: termForm.value.ratio,
      amount: termForm.value.amount,
      triggerCondition: termForm.value.triggerCondition,
      planDate: termForm.value.planDate || undefined,
      retentionMonths: termForm.value.retentionMonths,
      deductRule: termForm.value.deductRule
    }
    if (termForm.value.id) {
      await updatePayTerm(Number(termForm.value.id), payload)
    } else {
      await createPayTerm(payload)
    }
    ElMessage.success('条款已保存')
    termDialogVisible.value = false
    await loadPayTerms()
  } finally {
    termSubmitting.value = false
  }
}

async function removeTerm(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该收付款条款？历史计提不受影响。', '提示', { type: 'warning' })
  } catch {
    return
  }
  await deletePayTerm(Number(row.id))
  ElMessage.success('已删除')
  await loadPayTerms()
}
</script>

<style scoped>
.contract-drawer-content {
  padding: 0 var(--zw-space-sm);
}

.summary-card {
  padding: var(--zw-space-md);
  background-color: var(--zw-color-bg-base);
  border: 1px solid var(--zw-color-border-light);
  border-radius: var(--zw-border-radius-base);
}

.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-block {
  display: flex;
  align-items: center;
}

.main-title {
  font-size: var(--zw-font-size-md);
  color: var(--zw-color-text-primary);
}

.sub-code {
  color: var(--zw-color-text-secondary);
  font-size: var(--zw-font-size-sm);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--zw-space-md);
  margin-top: var(--zw-space-sm);
}

.kpi-item {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}

.kpi-label {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-color-text-secondary);
}

.kpi-value {
  font-size: var(--zw-font-size-md);
  color: var(--zw-color-text-primary);
}

.progress-section {
  background: var(--zw-color-bg-subtle, var(--zw-color-bg-base));
  padding: var(--zw-space-sm) var(--zw-space-md);
  border-radius: var(--zw-border-radius-sm);
}

.progress-row {
  display: flex;
  align-items: center;
}

.progress-label {
  width: 220px;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-color-text-secondary);
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb-2 {
  margin-bottom: var(--zw-space-xs);
}

.mb-3 {
  margin-bottom: var(--zw-space-sm);
}

.mb-4 {
  margin-bottom: var(--zw-space-md);
}

.mt-2 {
  margin-top: var(--zw-space-xs);
}

.mt-3 {
  margin-top: var(--zw-space-sm);
}

.ml-2 {
  margin-left: var(--zw-space-xs);
}

.ml-3 {
  margin-left: var(--zw-space-sm);
}

.my-2 {
  margin: var(--zw-space-xs) 0;
}

.text-xs {
  font-size: var(--zw-font-size-xs);
}

.text-secondary {
  color: var(--zw-color-text-secondary);
}

.font-medium {
  font-weight: 500;
}

.font-semibold {
  font-weight: 600;
}

.text-brand {
  color: var(--zw-color-primary);
}

.text-warning {
  color: var(--zw-color-warning);
}

.text-success {
  color: var(--zw-color-success);
}

.text-danger {
  color: var(--zw-color-danger);
}

.flex-1 {
  flex: 1;
}
</style>
