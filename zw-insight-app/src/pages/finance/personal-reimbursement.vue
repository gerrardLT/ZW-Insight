<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item"><text class="form-label">报销金额(元)</text><input v-model="form.amount" type="digit" placeholder="请输入报销总金额" class="form-input"/></view>
      <view class="form-item"><text class="form-label">报销事由</text><input v-model="form.reason" placeholder="请输入报销事由" class="form-input"/></view>
      <view class="form-item"><text class="form-label">费用类别</text>
        <view class="radio-group">
          <view class="radio-item" :class="{active:form.category==='交通'}" @click="form.category='交通'"><text>交通</text></view>
          <view class="radio-item" :class="{active:form.category==='餐饮'}" @click="form.category='餐饮'"><text>餐饮</text></view>
          <view class="radio-item" :class="{active:form.category==='住宿'}" @click="form.category='住宿'"><text>住宿</text></view>
          <view class="radio-item" :class="{active:form.category==='办公'}" @click="form.category='办公'"><text>办公</text></view>
          <view class="radio-item" :class="{active:form.category==='其他'}" @click="form.category='其他'"><text>其他</text></view>
        </view>
      </view>
      <view class="form-item"><text class="form-label">发生日期</text><input v-model="form.expenseDate" placeholder="YYYY-MM-DD" class="form-input"/></view>
      <view class="form-item"><text class="form-label">备注</text><input v-model="form.remark" placeholder="请输入备注" class="form-input"/></view>
    </view>
    <view class="form-section">
      <view class="section-title">票据附件</view>
      <button class="upload-btn" @click="chooseImage">+ 拍照/选择图片</button>
      <view class="image-list">
        <view class="image-item" v-for="(img,idx) in attachments" :key="idx">
          <image :src="img" mode="aspectFill" class="img-preview"/>
          <text class="img-delete" @click="attachments.splice(idx,1)">×</text>
        </view>
      </view>
    </view>
    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交报销</button>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savePersonalReimbursement } from '@/api/common'
const submitting=ref(false);const attachments=ref<string[]>([])
const form=ref({amount:'',reason:'',category:'交通',expenseDate:'',remark:''})
onMounted(()=>{const now=new Date();form.value.expenseDate=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`})
function chooseImage(){uni.chooseImage({count:3,success:(res)=>{attachments.value.push(...res.tempFilePaths)}})}
async function handleSubmit(){if(!form.value.amount){uni.showToast({title:'请输入金额',icon:'none'});return}if(!form.value.reason){uni.showToast({title:'请输入报销事由',icon:'none'});return}submitting.value=true;try{await savePersonalReimbursement({...form.value,amount:Number(form.value.amount),attachments:attachments.value});uni.showToast({title:'提交成功',icon:'success'});setTimeout(()=>{uni.navigateBack()},1500)}catch{}finally{submitting.value=false}}
</script>
<style scoped>
.form-page{padding:20rpx;padding-bottom:120rpx}.form-section{background:#fff;border-radius:12rpx;padding:24rpx;margin-bottom:20rpx}.section-title{font-size:28rpx;font-weight:bold;margin-bottom:16rpx;color:#303133}.form-item{display:flex;align-items:center;padding:24rpx 0;border-bottom:1rpx solid #f5f5f5}.form-item:last-child{border-bottom:none}.form-label{font-size:28rpx;color:#303133;min-width:160rpx}.form-input{flex:1;font-size:28rpx;color:#303133;text-align:right}.radio-group{display:flex;gap:12rpx;flex:1;justify-content:flex-end;flex-wrap:wrap}.radio-item{padding:6rpx 16rpx;border:1rpx solid #dcdfe6;border-radius:6rpx;font-size:24rpx;color:#606266}.radio-item.active{border-color:#409eff;color:#409eff;background:#ecf5ff}.upload-btn{background:#f5f7fa;border:2rpx dashed #c0c4cc;border-radius:8rpx;height:80rpx;line-height:80rpx;text-align:center;color:#606266;font-size:28rpx;margin-bottom:16rpx}.image-list{display:flex;flex-wrap:wrap;gap:12rpx}.image-item{position:relative;width:160rpx;height:160rpx}.img-preview{width:160rpx;height:160rpx;border-radius:8rpx}.img-delete{position:absolute;top:-10rpx;right:-10rpx;width:36rpx;height:36rpx;line-height:36rpx;text-align:center;background:#f56c6c;color:#fff;border-radius:50%;font-size:24rpx}.submit-btn{margin:40rpx 20rpx;height:88rpx;line-height:88rpx;background:#409eff;color:#fff;font-size:32rpx;border-radius:8rpx;border:none}
</style>
