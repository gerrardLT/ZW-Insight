<template>
  <view class="form-page">
    <view class="form-section">
      <view class="section-title">待归还备用金</view>
      <view v-if="pendingList.length===0" class="empty-tip">暂无待归还的备用金记录</view>
      <view class="fund-item" v-for="item in pendingList" :key="item.id" :class="{selected:form.fundId===item.id}" @click="selectFund(item)">
        <view class="fund-info"><text class="fund-amount">¥{{item.amount}}</text><text class="fund-date">{{item.applyDate}}</text></view>
        <text class="fund-purpose">{{item.purpose}}</text>
      </view>
    </view>
    <view class="form-section" v-if="form.fundId">
      <view class="form-item"><text class="form-label">归还金额(元)</text><input v-model="form.returnAmount" type="digit" placeholder="请输入归还金额" class="form-input"/></view>
      <view class="form-item"><text class="form-label">备注</text><input v-model="form.remark" placeholder="请输入备注" class="form-input"/></view>
    </view>
    <button class="submit-btn" :loading="submitting" :disabled="!form.fundId" @click="handleSubmit">提交归还</button>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPendingReserveFunds, returnReserveFund } from '@/api/common'
const submitting=ref(false);const pendingList=ref<any[]>([])
const form=ref({fundId:null as number|null,returnAmount:'',remark:''})
onMounted(async()=>{try{const res:any=await getPendingReserveFunds();pendingList.value=res.data||[]}catch{}})
function selectFund(item:any){form.value.fundId=item.id;form.value.returnAmount=String(item.amount)}
async function handleSubmit(){if(!form.value.fundId){uni.showToast({title:'请选择备用金记录',icon:'none'});return}if(!form.value.returnAmount){uni.showToast({title:'请输入归还金额',icon:'none'});return}submitting.value=true;try{await returnReserveFund({fundId:form.value.fundId,returnAmount:Number(form.value.returnAmount),remark:form.value.remark});uni.showToast({title:'归还成功',icon:'success'});setTimeout(()=>{uni.navigateBack()},1500)}catch{}finally{submitting.value=false}}
</script>
<style scoped>
.form-page{padding:20rpx;padding-bottom:120rpx}.form-section{background:#fff;border-radius:12rpx;padding:24rpx;margin-bottom:20rpx}.section-title{font-size:30rpx;font-weight:bold;margin-bottom:16rpx;color:#303133}.empty-tip{text-align:center;color:#909399;padding:40rpx 0;font-size:28rpx}.fund-item{padding:20rpx;border:2rpx solid #ebeef5;border-radius:8rpx;margin-bottom:12rpx}.fund-item.selected{border-color:#409eff;background:#ecf5ff}.fund-info{display:flex;justify-content:space-between;align-items:center}.fund-amount{font-size:32rpx;font-weight:bold;color:#303133}.fund-date{font-size:24rpx;color:#909399}.fund-purpose{font-size:26rpx;color:#606266;margin-top:8rpx}.form-item{display:flex;align-items:center;padding:24rpx 0;border-bottom:1rpx solid #f5f5f5}.form-item:last-child{border-bottom:none}.form-label{font-size:28rpx;color:#303133;min-width:180rpx}.form-input{flex:1;font-size:28rpx;color:#303133;text-align:right}.submit-btn{margin:40rpx 20rpx;height:88rpx;line-height:88rpx;background:#409eff;color:#fff;font-size:32rpx;border-radius:8rpx;border:none}
</style>
