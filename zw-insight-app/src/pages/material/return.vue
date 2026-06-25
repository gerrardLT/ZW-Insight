<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker=true"><text class="form-label">所属项目</text><view class="form-input picker"><text :class="{placeholder:!form.projectName}">{{form.projectName||'请选择项目'}}</text><text class="arrow">›</text></view></view>
    </view>
    <view class="form-section">
      <view class="form-item"><text class="form-label">关联入库单</text><input v-model="form.inboundCode" placeholder="请输入入库单号" class="form-input"/></view>
      <view class="form-item"><text class="form-label">材料名称</text><input v-model="form.materialName" placeholder="自动填充" class="form-input" disabled/></view>
      <view class="form-item"><text class="form-label">规格型号</text><input v-model="form.specification" placeholder="自动填充" class="form-input" disabled/></view>
      <view class="form-item"><text class="form-label">可退数量</text><text class="form-value">{{form.availableQty}}</text></view>
      <view class="form-item"><text class="form-label">退货数量</text><input v-model="form.returnQty" type="digit" placeholder="请输入退货数量" class="form-input"/></view>
      <view class="form-item"><text class="form-label">退货原因</text><input v-model="form.reason" placeholder="请输入退货原因" class="form-input"/></view>
    </view>
    <view class="form-section">
      <view class="section-title">退货凭证</view>
      <button class="upload-btn" @click="chooseImage">+ 拍照上传凭证</button>
      <view class="image-list">
        <view class="image-item" v-for="(img,idx) in attachments" :key="idx">
          <image :src="img" mode="aspectFill" class="img-preview"/>
          <text class="img-delete" @click="attachments.splice(idx,1)">×</text>
        </view>
      </view>
    </view>
    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交退货</button>
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker=false"><view class="picker-content" @click.stop><view class="picker-header"><text @click="showProjectPicker=false">取消</text><text class="picker-title">选择项目</text><text></text></view><scroll-view scroll-y class="picker-list"><view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)"><text>{{p.projectName}}</text></view></scroll-view></view></view>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjectList, saveMaterialReturn } from '@/api/common'
const submitting=ref(false);const showProjectPicker=ref(false);const projects=ref<any[]>([]);const attachments=ref<string[]>([])
const form=ref({projectId:null as number|null,projectName:'',inboundCode:'',materialName:'',specification:'',availableQty:0,returnQty:'',reason:''})
onMounted(async()=>{try{const res:any=await getProjectList({page:1,size:100});projects.value=res.data?.records||[]}catch{}})
function selectProject(p:any){form.value.projectId=p.id;form.value.projectName=p.projectName;showProjectPicker.value=false}
function chooseImage(){uni.chooseImage({count:3,sourceType:['camera','album'],success:(res)=>{attachments.value.push(...res.tempFilePaths)}})}
async function handleSubmit(){if(!form.value.projectId){uni.showToast({title:'请选择项目',icon:'none'});return}if(!form.value.returnQty||Number(form.value.returnQty)<=0){uni.showToast({title:'请输入退货数量',icon:'none'});return}if(Number(form.value.returnQty)>form.value.availableQty){uni.showToast({title:'退货数量不能超过可退数量',icon:'none'});return}submitting.value=true;try{await saveMaterialReturn({projectId:form.value.projectId,inboundCode:form.value.inboundCode,returnQty:Number(form.value.returnQty),reason:form.value.reason});uni.showToast({title:'退货提交成功',icon:'success'});setTimeout(()=>{uni.navigateBack()},1500)}catch{}finally{submitting.value=false}}
</script>
<style scoped>
.form-page{padding:20rpx;padding-bottom:120rpx}.form-section{background:#fff;border-radius:12rpx;padding:24rpx;margin-bottom:20rpx}.section-title{font-size:28rpx;font-weight:bold;margin-bottom:16rpx;color:#303133}.form-item{display:flex;align-items:center;padding:24rpx 0;border-bottom:1rpx solid #f5f5f5}.form-item:last-child{border-bottom:none}.form-label{font-size:28rpx;color:#303133;min-width:160rpx}.form-input{flex:1;font-size:28rpx;color:#303133;text-align:right}.form-input.picker{display:flex;align-items:center;justify-content:flex-end}.form-value{flex:1;text-align:right;font-size:28rpx;color:#606266}.placeholder{color:#c0c4cc}.arrow{margin-left:8rpx;color:#c0c4cc;font-size:32rpx}.upload-btn{background:#f5f7fa;border:2rpx dashed #c0c4cc;border-radius:8rpx;height:80rpx;line-height:80rpx;text-align:center;color:#606266;font-size:28rpx;margin-bottom:16rpx}.image-list{display:flex;flex-wrap:wrap;gap:12rpx}.image-item{position:relative;width:160rpx;height:160rpx}.img-preview{width:160rpx;height:160rpx;border-radius:8rpx}.img-delete{position:absolute;top:-10rpx;right:-10rpx;width:36rpx;height:36rpx;line-height:36rpx;text-align:center;background:#f56c6c;color:#fff;border-radius:50%;font-size:24rpx}.submit-btn{margin:40rpx 20rpx;height:88rpx;line-height:88rpx;background:#409eff;color:#fff;font-size:32rpx;border-radius:8rpx;border:none}.picker-mask{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);z-index:999;display:flex;align-items:flex-end}.picker-content{width:100%;background:#fff;border-radius:24rpx 24rpx 0 0;max-height:70vh}.picker-header{display:flex;justify-content:space-between;align-items:center;padding:24rpx 32rpx;border-bottom:1rpx solid #f0f0f0}.picker-title{font-size:30rpx;font-weight:bold}.picker-list{max-height:60vh}.picker-item{padding:24rpx 32rpx;border-bottom:1rpx solid #f5f5f5;font-size:28rpx}
</style>
