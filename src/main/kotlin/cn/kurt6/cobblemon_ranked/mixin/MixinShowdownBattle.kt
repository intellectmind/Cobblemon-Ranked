//package cn.kurt6.cobblemon_ranked.mixin
//
//import com.cobblemon.mod.common.battles.ShowdownActionResponse
//import com.cobblemon.mod.common.battles.ShowdownActionResponseType
//import com.cobblemon.mod.common.battles.ShowdownBattle
//import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
//import net.minecraft.server.network.ServerPlayerEntity
//import net.minecraft.text.Text
//import org.spongepowered.asm.mixin.Mixin
//import org.spongepowered.asm.mixin.injection.At
//import org.spongepowered.asm.mixin.injection.Inject
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
//
//@Mixin(ShowdownBattle::class)
//class MixinShowdownBattle {
//
//    @Inject(method = "handleAction", at = @At("HEAD"), cancellable = true)
//    fun onHandleAction(actor: PlayerBattleActor, response: ShowdownActionResponse, ci: CallbackInfo) {
//        if (response.type == ShowdownActionResponseType.FORFEIT) {
//            val player = actor.entity as? ServerPlayerEntity ?: return
//            player.sendMessage(Text.literal("§c排位战禁止弃权！"))
//            ci.cancel() // 拦截弃权
//        }
//    }
//}
