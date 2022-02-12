package io.github.lightman314.lctech.client.gui.screen.inventory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import io.github.lightman314.lctech.LCTech;
import io.github.lightman314.lctech.client.gui.widget.button.EnergyTradeButton;
import io.github.lightman314.lctech.container.EnergyTraderContainer;
import io.github.lightman314.lctech.trader.energy.IEnergyTrader;
import io.github.lightman314.lightmanscurrency.client.gui.screen.TradingTerminalScreen;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.IconButton;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.client.util.TextInputUtil;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.cashregister.MessageCRNextTrader;
import io.github.lightman314.lightmanscurrency.network.message.cashregister.MessageCRSkipTo;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageCollectCoins;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageExecuteTrade;
import io.github.lightman314.lightmanscurrency.trader.permissions.Permissions;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import io.github.lightman314.lightmanscurrency.util.MoneyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public class EnergyTraderScreen extends ContainerScreen<EnergyTraderContainer>{

	public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(LCTech.MODID, "textures/gui/container/energy_trader.png");
	
	public static final int ENERGY_BAR_HEIGHT = 55;
	
	Button buttonShowStorage;
	Button buttonCollectMoney;
	
	//Universal Trader
	Button buttonBack;
	
	//Cash Register Trader
	Button buttonLeft;
	Button buttonRight;
	TextFieldWidget pageInput;
	Button buttonSkipToPage;
	
	List<EnergyTradeButton> tradeButtons = new ArrayList<>();
	
	public EnergyTraderScreen(EnergyTraderContainer menu, PlayerInventory inventory, ITextComponent title) {
		super(menu, inventory, title);
		this.xSize = 176;
		this.ySize = 203;
	}
	
	@Override
	@SuppressWarnings("deprecation")
	protected void drawGuiContainerBackgroundLayer(MatrixStack matrix, float partialTicks, int mouseX, int mouseY)
	{
		
		Minecraft.getInstance().getTextureManager().bindTexture(GUI_TEXTURE);
		RenderSystem.color4f(1f, 1f, 1f, 1f);
		
		//Render the background
		this.blit(matrix, this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
		
		//Render the energy bar
		double fillPercent = (double)this.container.getTrader().getTotalEnergy() / (double)this.container.getTrader().getMaxEnergy();
		int fillHeight = MathUtil.clamp((int)(ENERGY_BAR_HEIGHT * fillPercent), 0, ENERGY_BAR_HEIGHT);
		int yOffset = ENERGY_BAR_HEIGHT - fillHeight;
		this.blit(matrix, this.guiLeft + 8, this.guiTop+ 18 + yOffset, this.xSize, yOffset, 16, fillHeight);
		
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(MatrixStack matrix, int mouseX, int mouseY)
	{
		this.font.drawString(matrix, this.container.getTrader().getTitle().getString(), 8f, 6f, 0x404040);
		this.font.drawString(matrix, this.playerInventory.getName().getString(), 8f, this.ySize - 94f, 0x404040);
		this.font.drawString(matrix, new TranslationTextComponent("tooltip.lightmanscurrency.credit", MoneyUtil.getStringOfValue(this.container.GetCoinValue())).getString(), 80f, this.ySize - 124f, 0x404040);
	}
	
	@Override
	protected void init()
	{
		
		super.init();
		
		this.buttonShowStorage = this.addButton(new IconButton(this.guiLeft - 20, this.guiTop, this::PressStorageButton, this.font, IconData.of(Items.CHEST)));
		this.buttonShowStorage.visible = this.container.hasPermission(Permissions.OPEN_STORAGE) && !this.container.isCashRegister();
		
		this.buttonCollectMoney = this.addButton(new IconButton(this.guiLeft - 20, this.guiTop + 20, this::PressCollectionButton, this.font, IconData.of(GUI_TEXTURE, this.xSize + 16, 0)));
		this.buttonCollectMoney.active = false;
		this.buttonCollectMoney.visible = this.container.hasPermission(Permissions.COLLECT_COINS) && !this.container.getTrader().getCoreSettings().hasBankAccount();
		
		//Universal Widget(s)
		this.buttonBack = this.addButton(new IconButton(this.guiLeft - 20, this.guiTop + 40, this::PressBackButton, this.font, IconData.of(GUI_TEXTURE, this.xSize + 32, 0)));
		this.buttonBack.visible = this.container.isUniversal();
		
		//Cash Register Widget(s)
		if(this.container.isCashRegister() && this.container.getCashRegister().getPairedTraderSize() > 1)
		{
			this.buttonLeft = this.addButton(new IconButton(this.guiLeft - 20, this.guiTop, this::PressArrowButton, this.font, IconData.of(GUI_TEXTURE, this.xSize + 16, 16)));
			this.buttonRight = this.addButton(new IconButton(this.guiLeft + this.xSize, this.guiTop, this::PressArrowButton, this.font, IconData.of(GUI_TEXTURE, this.xSize + 32, 16)));
			
			this.pageInput = this.addListener(new TextFieldWidget(this.font, this.guiLeft + 50, this.guiTop - 19, this.xSize - 120, 18, new StringTextComponent("")));
			this.pageInput.setMaxStringLength(2);
			this.pageInput.setText(String.valueOf(this.container.getThisCRIndex() + 1));
			
			this.buttonSkipToPage = this.addButton(new IconButton(this.guiLeft + this.xSize - 68, this.guiTop - 20, this::PressPageSkipButton, this.font, IconData.of(GUI_TEXTURE, this.xSize + 32, 16)));
			this.buttonSkipToPage.active = false;
		}
		
		this.initTradeButtons();
		
	}
	
	protected void initTradeButtons()
	{
		for(int y = 0; y < 2; ++y)
		{
			for(int x = 0; x < 2; ++x)
			{
				this.tradeButtons.add(this.addButton(new EnergyTradeButton(this.guiLeft + 28 + 73 * x, this.guiTop + 17 + 31 * y, this::PressTradeButton, x + 2 * y, this, this.font, () -> this.container.getTrader(), () -> this.container.GetCoinValue(), () -> this.container.getBatteryStack())));
			}
		}
	}
	
	@Override
	public void tick()
	{
		
		super.tick();
		
		this.buttonShowStorage.visible = this.container.hasPermission(Permissions.OPEN_STORAGE) && !this.container.isCashRegister();
		
		if(this.container.hasPermission(Permissions.COLLECT_COINS))
		{
			this.buttonCollectMoney.visible = !this.container.getTrader().getCoreSettings().hasBankAccount();
			this.buttonCollectMoney.active = this.container.getTrader().getStoredMoney().getRawValue() > 0;
			if(!this.buttonCollectMoney.active)
				this.buttonCollectMoney.visible = !this.container.getTrader().getCoreSettings().isCreative();
		}
		else
			this.buttonCollectMoney.visible = false;
		
		if(this.buttonSkipToPage != null)
		{
			if(this.pageInput != null)
				TextInputUtil.whitelistInteger(this.pageInput, 1, this.container.getTotalCRSize());
			int pageInputValue = this.getPageInput();
			this.buttonSkipToPage.active = pageInputValue >= 0 && pageInputValue < this.container.getTotalCRSize() && pageInputValue != this.container.getThisCRIndex();
		}
		
	}
	
	@Override
	public void render(MatrixStack pose, int mouseX, int mouseY, float partialTicks)
	{
		this.renderBackground(pose);
		super.render(pose, mouseX, mouseY, partialTicks);
		this.renderHoveredTooltip(pose, mouseX, mouseY);
		
		if(this.buttonShowStorage != null && this.buttonShowStorage.isMouseOver(mouseX, mouseY))
		{
			this.renderTooltip(pose, new TranslationTextComponent("tooltip.lightmanscurrency.trader.openstorage"), mouseX, mouseY);
		}
		else if(this.buttonCollectMoney != null && this.buttonCollectMoney.isMouseOver(mouseX, mouseY))
		{
			this.renderTooltip(pose, new TranslationTextComponent("tooltip.lightmanscurrency.trader.collectcoins", this.container.getTrader().getStoredMoney().getString()), mouseX, mouseY);
		}
		else if(this.buttonBack != null && this.buttonBack.isMouseOver(mouseX, mouseY))
		{
			this.renderTooltip(pose, new TranslationTextComponent("tooltip.lightmanscurrency.trader.universaltrader.back"), mouseX, mouseY);
		}
		else if(this.isMouseOverEnergy(mouseX, mouseY))
		{
			this.func_243308_b(pose, IEnergyTrader.getEnergyHoverTooltip(this.container.getTrader()), mouseX, mouseY);
		}
		for(int i = 0; i < this.tradeButtons.size(); ++i)
		{
			this.tradeButtons.get(i).tryRenderTooltip(pose, this, this.container.getTrader(), mouseX, mouseY);
		}
	}
	
	private boolean isMouseOverEnergy(int mouseX, int mouseY)
	{
		return mouseX >= this.guiLeft + 7 && mouseX < this.guiLeft + 7 + 18 && mouseY >= this.guiTop + 17 && mouseY < this.guiTop + 17 + 57;
	}
	
	private void PressStorageButton(Button button)
	{
		if(this.container.hasPermission(Permissions.OPEN_STORAGE))
		{
			this.container.getTrader().sendOpenStorageMessage();
		}
	}
	
	private void PressCollectionButton(Button button)
	{
		if(this.container.hasPermission(Permissions.COLLECT_COINS))
		{
			LightmansCurrencyPacketHandler.instance.sendToServer(new MessageCollectCoins());
		}
	}
	
	private void PressTradeButton(Button button)
	{
		int tradeIndex = 0;
		if(tradeButtons.contains(button))
			tradeIndex = tradeButtons.indexOf(button);
		LightmansCurrencyPacketHandler.instance.sendToServer(new MessageExecuteTrade(tradeIndex));
	}
	
	private void PressBackButton(Button button)
	{
		this.minecraft.displayGuiScreen(new TradingTerminalScreen());
	}

	private void PressArrowButton(Button button)
	{
		int direction = 1;
		if(button == this.buttonLeft)
			direction = -1;
		
		LightmansCurrencyPacketHandler.instance.sendToServer(new MessageCRNextTrader(direction));
	}
	
	private void PressPageSkipButton(Button button)
	{
		int page = this.getPageInput();
		LightmansCurrencyPacketHandler.instance.sendToServer(new MessageCRSkipTo(page));
	}
	
	private int getPageInput()
	{
		if(this.pageInput != null)
			return TextInputUtil.getIntegerValue(this.pageInput) - 1;
		return -1;
	}
	
	
	
}
