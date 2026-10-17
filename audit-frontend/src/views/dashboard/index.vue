<template>
  <div class="page-container">
    <el-card shadow="never" class="welcome-card">
      <div class="welcome-title">
        你好，{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}
      </div>
      <div class="welcome-sub">欢迎使用审计日志系统</div>
    </el-card>

    <el-row :gutter="16" class="info-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>我的角色</span>
          </template>
          <el-tag v-for="role in userStore.roles" :key="role" class="tag-item" type="success">
            {{ role }}
          </el-tag>
          <el-empty v-if="userStore.roles.length === 0" description="暂无角色" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>我的权限标识（{{ userStore.permissions.length }} 个）</span>
          </template>
          <el-tag v-for="perm in userStore.permissions" :key="perm" class="tag-item" type="info">
            {{ perm }}
          </el-tag>
          <el-empty
            v-if="userStore.permissions.length === 0"
            description="暂无权限"
            :image-size="60"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="tip-card">
      <template #header>
        <span>这个系统的权限是从哪来的</span>
      </template>
      <ul class="tip-list">
        <li>
          <b>登录授权：</b>本系统没有登录页与用户表，账号密码由 RBAC 的授权服务器（rbac-oauth）校验；
          也支持从授权服务器跳转回来完成登录。
        </li>
        <li>
          <b>权限下发：</b>菜单与权限点由 RBAC 统一配置，经 RocketMQ 推送到本系统，
          收到消息后重建本地的同步表。
        </li>
        <li>
          <b>权限判定：</b>每个请求都按同步下来的权限点实时判权，
          在 RBAC 里调整授权后，本系统下一次请求即生效，无需重新登录。
        </li>
        <li>
          <b>超级管理员：</b>admin 的权限在 RBAC 里不落库，本系统按角色标识识别，
          直接给全部菜单与权限。
        </li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
</script>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}

.welcome-title {
  font-size: 20px;
  font-weight: 600;
}

.welcome-sub {
  margin-top: 8px;
  color: #909399;
}

.info-row {
  margin-bottom: 16px;
}

.tag-item {
  margin: 0 8px 8px 0;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  color: #606266;
}
</style>
