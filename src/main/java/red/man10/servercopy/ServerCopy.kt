package red.man10.servercopy

import com.sk89q.worldedit.bukkit.WorldEditPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.kotlin.MySQLManager
import java.sql.ResultSet
import java.util.concurrent.ExecutionException
import org.bukkit.scheduler.BukkitRunnable
import java.util.*




class ServerCopy : JavaPlugin(){

    val prefix = "§l[§b§lServer§e§lCopy§f§l]"
    val l = Bukkit.getLogger()

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        getCommand("scopy").executor = this

        val c = MysqlTableCreate(this)
        c.start()

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    override fun onCommand(sender: CommandSender?, command: Command?, label: String?, args: Array<out String>?): Boolean {

        if (sender !is Player)return false

        val p: Player = sender

        if (!p.hasPermission("scopy.use"))return false

        if (args == null || args.isEmpty()){

            sender.sendMessage("§f§l--------${prefix}§f§l--------")
            sender.sendMessage("§6§l/scopy copy [名前]§f§r:WEで選択してる範囲を保存する")
            sender.sendMessage("§6§l/scopy paste [名前]§f§r:[名前]の建造物を張り付ける")
            sender.sendMessage("§6§l/scopy list§f§r:登録されてる名前の一覧が見れる")
            sender.sendMessage("§bcreated by Ryotackey")
            sender.sendMessage("§l--------------------------------------------")

            return true
        }

        if (args.size > 2)return false

        if (args.size == 1){

            if (args[0].equals("list", ignoreCase = true)){

                val m = getList(this, p)
                m.start()

            }

        }

        if (args[0].equals("copy", ignoreCase = true)) {

            val we = getWorldEdit()

            if (we == null) {
                p.sendMessage("${prefix}§cWorldEditが入ってません")
                return true
            }

            val sel = we.getSelection(p)

            if(sel == null){
                p.sendMessage(prefix + "§c先にWorldEditで選択する必要があります");
                return true
            }

            val min = sel.minimumPoint
            val max = sel.maximumPoint

            val e = SaveBlockData(this, min, max, args[1], p)
            e.start()

        }

        if (args[0].equals("paste", ignoreCase = true)){

            val m = LoadBlockData(this, p, args[1])
            m.start()

        }

        return true
    }

    fun getWorldEdit(): WorldEditPlugin? {

        val p = Bukkit.getServer().pluginManager.getPlugin("WorldEdit")

        if (p is WorldEditPlugin)return p
        else return null

    }



    fun blockPlace(bd: BlockData, p: Player){

        var count = 0

        val material = bd.mate
        val data = bd.data
        val loc = bd.loc

        object : BukkitRunnable() {

            val amount = 1000

            @Synchronized
            override fun run() {

                var count2 = 0

                for (i in 0 until loc.size){

                    if (count > count2) {
                        count2++
                        continue
                    }

                    val world = loc[i].world

                    val b = world.getBlockAt(loc[i])

                    b.type = Material.getMaterial(material[i])
                    b.data = data[i]

                    count++

                    if (count == loc.size-1){
                        cancel()
                        p.sendMessage("§a完了しました")
                        return
                    }

                    if (count % amount ==0)break

                    count2++
                }

            }
        }.runTaskTimer(this, 0, 20)
    }

    class BlockData{

        var mate = mutableListOf<Int>()
        var data = mutableListOf<Byte>()

        var loc = mutableListOf<Location>()

    }

}

class MysqlTableCreate(val plugin : ServerCopy): Thread(){

    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        mysql.execute("CREATE TABLE `servercopytable` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `name` varchar(45) DEFAULT NULL,\n" +
                "  `material` longtext,\n" +
                "  `data` longtext,\n" +
                "  `startx` int(11) DEFAULT NULL,\n" +
                "  `starty` int(11) DEFAULT NULL,\n" +
                "  `startz` int(11) DEFAULT NULL,\n" +
                "  `endx` int(11) DEFAULT NULL,\n" +
                "  `endy` int(11) DEFAULT NULL,\n" +
                "  `endz` int(11) DEFAULT NULL,\n" +
                "  `save_date` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8;")

    }

}

class SaveBlockData(val plugin: ServerCopy, val min: Location, val max: Location, val title: String, val p: Player): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        var rs: ResultSet? = null

        try {
            rs = mysql.query("SELECT count(1) FROM servercopytable WHERE name='$title';")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        if (rs == null){
            p.sendMessage("§cテーブル情報が読み込めないため失敗しました")
            mysql.close()
            return
        }

        rs.first()

        if (rs.getInt("count(1)") != 0){
            p.sendMessage("§c既に登録されています")
            mysql.close()
            return
        }
        mysql.close()

        val startX = Math.min(min.getBlockX(), max.getBlockX())
        val endX = Math.max(min.getBlockX(), max.getBlockX())

        val startY = Math.min(min.getBlockY(), max.getBlockY())
        val endY = Math.max(min.getBlockY(), max.getBlockY())

        val startZ = Math.min(min.getBlockZ(), max.getBlockZ())
        val endZ = Math.max(min.getBlockZ(), max.getBlockZ())

        val blocks = ArrayList<Block>()
        val w = min.world

        for (x in startX..endX) {
            for (y in startY..endY) {
                for (z in startZ..endZ) {

                    val loc = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
                    val block = loc.block

                    blocks.add(block)
                }
            }
        }

        val bd = blockToBlockData(blocks)

        val matelist = bd.mate
        val datalist = bd.data

        for (i in 0 until matelist.size) {

            val b = mysql.execute("INSERT INTO `servercopytable` (`name`, `material`, `data`, `startx`, `starty`, `startz`, `endx`, `endy`, `endz`) " +
                    "VALUES ('${title}', '${matelist[i]}', '${datalist[i]}', '${startX}', '$startY', '$startZ', '$endX', '$endY', '$endZ');")
            if (b) {
                p.sendMessage("§e${i+1}/${matelist.size}§fsave done")
            } else {
                p.sendMessage("§c保存に失敗しました")
                return
            }

        }

        p.sendMessage("§a保存が完了しました")
    }

    fun blockToBlockData(blocklist: MutableList<Block>): BlockData{

        val bd = BlockData()

        val list = devide(blocklist, 400000)

        val matelist = mutableListOf<String>()
        val datalist = mutableListOf<String>()

        var startloc= mutableListOf<Location>()
        var endloc = mutableListOf<Location>()

        var count = 0

        for (i in list){
            var matestr = ""
            var datastr = ""
            for (j in i) {
                if (j == i.first())startloc.add(j.location)
                if (j==i.last())endloc.add(j.location)
                matestr += ":" + j.type.id.toString()
                datastr += ":" + j.data.toString()
                count++

                if (count%1000==0) {
                    p.sendMessage(count.toString() + " §eblock done")
                }

            }

            matelist.add(matestr)
            datalist.add(datastr)

        }

        bd.mate = matelist
        bd.data = datalist

        return bd

    }

    fun <T> devide(origin: MutableList<T>?, size: Int): List<List<T>> {
        if (origin == null || origin.isEmpty() || size <= 0) {
            return Collections.emptyList()
        }

        val block = origin.size / size + if (origin.size % size > 0) 1 else 0

        val devidedList = ArrayList<List<T>>(block)
        for (i in 0 until block) {
            val start = i * size
            val end = Math.min(start + size, origin.size)
            devidedList.add(ArrayList(origin.subList(start, end)))
        }
        return devidedList
    }

    class BlockData{

        var mate = mutableListOf<String>()
        var data = mutableListOf<String>()

    }

}

class LoadBlockData(val plugin: ServerCopy, val p: Player, val title: String): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        var count: ResultSet? = null

        try {
            count = mysql.query("SELECT count(1) FROM servercopytable WHERE name='$title';")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        if (count == null){
            p.sendMessage("§cテーブル情報が読み込めないため失敗しました")
            mysql.close()
        }else {

            count.first()

            val counti = count.getInt("count(1)")

            if (counti == 0) {
                p.sendMessage("§c登録されていません")
                mysql.close()
                return
            }

            var rs: ResultSet? = null

            try {
                rs = mysql.query("SELECT * FROM servercopytable WHERE name='$title';")
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }

            if (rs == null) {
                p.sendMessage("§cテーブル情報が読み込めないか、データが存在しないため失敗しました")
                mysql.close()

            } else {

                val min = p.location.block.location

                var str = ""
                var str2 = ""

                while (rs.next()){

                    str += rs.getString("material")
                    str2 += rs.getString("data")

                }

                rs.first()

                val x1 = rs.getInt("endx") - rs.getInt("startx")
                val y1 = rs.getInt("endy") - rs.getInt("starty")
                val z1 = rs.getInt("endz") - rs.getInt("startz")

                val x2 = min.x + x1
                val y2 = min.y + y1
                val z2 = min.z + z1

                val max = Location(min.world, x2, y2, z2)

                val bd = stringToBlockData(str, str2, min, max)

                plugin.blockPlace(bd, p)

                mysql.close()

            }
        }

    }

    fun stringToBlockData(mate: String, data: String, min: Location, max: Location): ServerCopy.BlockData {

        var bd = ServerCopy.BlockData()

        val matelist = mutableListOf<Int>()
        val datalist = mutableListOf<Byte>()

        val matestr = mate.split(":") as MutableList<String>
        val datastr = data.split(":") as MutableList<String>

        for (i in matestr){

            var id: Int

            try {
                id = i.toInt()
            }catch (n: NumberFormatException){
                continue
            }
            matelist.add(id)
        }

        for (i in datastr){

            var id: Byte

            try {
                id = i.toByte()
            }catch (n: NumberFormatException){
                continue
            }
            datalist.add(id)
        }

        val loc = mutableListOf<Location>()

        val startX = Math.min(min.getBlockX(), max.getBlockX())
        val endX = Math.max(min.getBlockX(), max.getBlockX())

        val startY = Math.min(min.getBlockY(), max.getBlockY())
        val endY = Math.max(min.getBlockY(), max.getBlockY())

        val startZ = Math.min(min.getBlockZ(), max.getBlockZ())
        val endZ = Math.max(min.getBlockZ(), max.getBlockZ())

        val blocks = ArrayList<Block>()
        val w = min.world

        for (x in startX..endX) {
            for (y in startY..endY) {
                for (z in startZ..endZ) {

                    val loc1 = Location(w, x.toDouble(), y.toDouble(), z.toDouble())

                    loc.add(loc1)

                }
            }
        }

        bd.mate = matelist
        bd.data = datalist
        bd.loc = loc

        return bd
    }

}

class getList(val plugin: ServerCopy, val p: Player): Thread(){

    @Synchronized
    override fun run() {

        val mysql = MySQLManager(plugin, "ServerCopy")

        var rs: ResultSet? = null

        try {
            rs = mysql.query("SELECT * FROM servercopytable")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        if (rs == null) {
            p.sendMessage("§cテーブル情報が読み込めないか、データが存在しないため失敗しました")
            mysql.close()
        }else{

            p.sendMessage(plugin.prefix + "§a登録名一覧")

            var s = ""

            var count = 0

            while (rs.next()){

                if (s == rs.getString("name"))continue

                s = rs.getString("name")

                p.sendMessage("§l" + count + ":§e§l" + s)

                count++

            }

        }

    }

}
