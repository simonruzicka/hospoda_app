package com.example.h1

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.h1.data.*
import com.example.h1.ui.theme.H1Theme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.content.Context
import androidx.compose.ui.platform.LocalContext



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            H1Theme {
                Surface(modifier = Modifier.fillMaxSize()) { NalevnaApp() }
            }
        }
    }
}

@Composable
fun NalevnaApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()


    val prefs = remember { context.getSharedPreferences("nalevna_prefs", Context.MODE_PRIVATE) }
    val savedName = prefs.getString("user_name", "") ?: ""

    var currentScreen by remember { mutableStateOf(if (savedName.isNotEmpty()) "home" else "welcome") }
    var userName by remember { mutableStateOf(savedName) }

    var selectedPubId by remember { mutableStateOf<Int?>(null) }
    var selectedFriendId by remember { mutableStateOf<Int?>(null) }
    var selectedSessionId by remember { mutableStateOf<Int?>(null) }
    var sessionParticipants by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(
        bottomBar = {
            if (currentScreen in listOf("home", "pub_detail", "friends_list", "friend_detail", "history", "session_detail", "profile")) {
                BottomNavBar(currentScreen = currentScreen, onNavigate = { currentScreen = it })
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                "welcome" -> WelcomeScreen { jmeno ->
                    prefs.edit().putString("user_name", jmeno).apply()
                    userName = jmeno
                    currentScreen = "home"
                }

                "home" -> HomeScreen(sklonujJmeno(userName), db.pubDao(), { currentScreen = "create_pub" }) { id ->
                    selectedPubId = id
                    sessionParticipants = emptySet<Int>()
                    currentScreen = "pub_detail"
                }

                "create_pub" -> CreatePubScreen(
                    onBack = { currentScreen = "home" },
                    onSave = { name, desc, uri, drinks ->
                        uri?.let {
                            try {
                                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        scope.launch {
                            val id = db.pubDao().insertPub(Pub(name = name, description = desc, imageUri = uri?.toString()))
                            db.pubDao().insertDrinks(drinks.map { it.copy(pubId = id.toInt()) })
                            currentScreen = "home"
                        }
                    }
                )

                "profile" -> ProfileScreen(userName) { noveJmeno ->
                    prefs.edit().putString("user_name", noveJmeno).apply()
                    userName = noveJmeno
                    currentScreen = "home"
                }

                "pub_detail" -> {
                    if (selectedPubId != null) {
                        PubDetailScreen(selectedPubId!!, db.pubDao(), sessionParticipants, { sessionParticipants = it }, { currentScreen = "home" }, { currentScreen = "drinking_session" })
                    }
                }

                "drinking_session" -> {
                    if (selectedPubId != null) {
                        DrinkingSessionScreen(selectedPubId!!, userName, sessionParticipants, db.pubDao(), null, { currentScreen = "history" })
                    }
                }

                "drinking_session_resume" -> {
                    if (selectedPubId != null) {
                        DrinkingSessionScreen(selectedPubId!!, userName, sessionParticipants, db.pubDao(), selectedSessionId, { currentScreen = "history" })
                    }
                }

                "history" -> HistoryScreen(userName, db.pubDao()) { sId -> selectedSessionId = sId; currentScreen = "session_detail" }

                "session_detail" -> {
                    if (selectedSessionId != null) {
                        SessionDetailScreen(selectedSessionId!!, db.pubDao(), userName, { currentScreen = "history" }, { pId, part -> selectedPubId = pId; sessionParticipants = part; currentScreen = "drinking_session_resume" }, { currentScreen = "history" })
                    }
                }

                "friends_list" -> FriendsScreen(userName, db.pubDao()) { id -> selectedFriendId = id; currentScreen = "friend_detail" }

                "friend_detail" -> {
                    if (selectedFriendId != null) {
                        FriendDetailScreen(selectedFriendId!!, db.pubDao()) { currentScreen = "friends_list" }
                    }
                }
            }
        }
    }
}


@Composable
fun SessionDetailScreen(
    sessionId: Int, dao: PubDao, userName: String,
    onBack: () -> Unit, onResume: (Int, Set<Int>) -> Unit, onDelete: () -> Unit
) {
    val session by dao.getSessionById(sessionId).collectAsState(null)
    val allPubs by dao.getAllPubs().collectAsState(emptyList())
    val pub = allPubs.find { it.id == session?.pubId }
    val consumptions by dao.getConsumptionsForSessionFlow(sessionId).collectAsState(emptyList())
    val allFriends by dao.getAllPersons().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    val activePeopleIds = consumptions.map { it.personId }.toSet()
    val activePeople = listOf(Person(-1, userName)) + allFriends.filter { activePeopleIds.contains(it.id) }

    val grandTotal = session?.totalSpent ?: 0
    val dateStr = session?.let { SimpleDateFormat("d. M. yyyy (HH:mm)", Locale.getDefault()).format(Date(it.dateMillis)) } ?: ""

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(24.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Zpět",
                    modifier = Modifier.size(40.dp).clickable { onBack() },
                    tint = Color.Black
                )


                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = pub?.name?.uppercase() ?: "NÁLEVNA",
                        fontSize = 32.sp, // Trochu upraveno pro lepší čitelnost
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )
                    Text(
                        text = dateStr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }


                Spacer(Modifier.width(40.dp))
            }


            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(activePeople) { person ->
                    val personsConsumptions = consumptions.filter { it.personId == person.id }
                    val total = personsConsumptions.sumOf { it.price }

                    if (personsConsumptions.isNotEmpty() || person.id == -1) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(16.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(person.name, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                    Text("$total Kč", fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(text = personsConsumptions.joinToString("") { it.drinkIcon }, fontSize = 28.sp, lineHeight = 34.sp)
                            }
                        }
                    }
                }
            }


            Spacer(Modifier.height(0.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp) // 4dp tloušťka, aby hezky ladila s tvými 3dp okraji
                    .background(Color.Black, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(30.dp))

            // --- CELKOVÁ ÚTRATA ---
            Text(
                text = "Celková útrata - $grandTotal Kč",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            // ----------------------------------------

            Spacer(Modifier.height(24.dp))

            // TLAČÍTKA
            HeavyButton("Pokračovat v chlastání 🍻", onClick = {
                pub?.let { onResume(it.id, activePeopleIds.filter { id -> id != -1 }.toSet()) }
            }, enabled = true)
            Spacer(Modifier.height(12.dp))
            HeavyButton("Smazat záznam 🗑️", onClick = {
                scope.launch {
                    dao.deleteSessionById(sessionId)
                    dao.deleteConsumptionsForSession(sessionId)
                    onDelete()
                }
            }, enabled = true)
            Spacer(Modifier.height(32.dp))
        }
    }
}

// =====================================================================================
// --- OBRAZOVKA: DRINKING SESSION ---
// =====================================================================================
@Composable
fun DrinkingSessionScreen(
    pubId: Int, userName: String, initialParticipants: Set<Int>, dao: PubDao,
    existingSessionId: Int?,
    onEndSession: () -> Unit
) {
    val pub = dao.getAllPubs().collectAsState(emptyList()).value.find { it.id == pubId }
    val drinksMenu by dao.getDrinksForPub(pubId).collectAsState(emptyList())
    val allFriends by dao.getAllPersons().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var activeParticipantIds by remember { mutableStateOf(initialParticipants) }
    val activePeople = listOf(Person(-1, userName)) + allFriends.filter { activeParticipantIds.contains(it.id) }

    val consumptions = remember { mutableStateListOf<Pair<Int, Drink>>() }

    LaunchedEffect(existingSessionId) {
        if (existingSessionId != null) {
            val pastConsumptions = dao.getConsumptionsList(existingSessionId)
            pastConsumptions.forEach { c ->
                consumptions.add(Pair(c.personId, Drink(0, pubId, c.drinkName, c.price, c.drinkIcon)))
            }
            activeParticipantIds = activeParticipantIds + pastConsumptions.map { it.personId }.filter { it != -1 }.toSet()
        }
    }

    val grandTotal = consumptions.sumOf { it.second.price }
    val todayDate = SimpleDateFormat("d. M. yyyy", Locale.getDefault()).format(Date())

    var addDrinkForPerson by remember { mutableStateOf<Int?>(null) }
    var showEditChoiceDialog by remember { mutableStateOf(false) }
    var showEditParticipants by remember { mutableStateOf(false) }
    var showAddNewFriend by remember { mutableStateOf(false) }
    var showDrinkEditor by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // NÁZEV HOSPODY (Zvětšeno a vycentrováno)
            Text(
                text = pub?.name?.uppercase() ?: "NÁLEVNA",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                lineHeight = 46.sp
            )
            // DATUM (Pod názvem)
            Text(
                text = todayDate,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(activePeople) { person ->
                    val personsDrinks = consumptions.filter { it.first == person.id }.map { it.second }
                    val total = personsDrinks.sumOf { it.price }

                    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(person.name, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                    Text("$total Kč", fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(text = personsDrinks.joinToString("") { it.icon }, fontSize = 28.sp, lineHeight = 34.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Box(
                                modifier = Modifier.size(56.dp).background(Color.Black, RoundedCornerShape(12.dp)).clickable { addDrinkForPerson = person.id },
                                contentAlignment = Alignment.Center
                            ) { Text("+", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Celková útrata - $grandTotal Kč", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.size(80.dp).background(Color(0xFFE5D5C1), CircleShape).border(4.dp, Color.Black, CircleShape).clickable { showEditChoiceDialog = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, contentDescription = "Upravit", Modifier.size(40.dp))
                }
                Box(Modifier.size(80.dp).background(Color(0xFFE5D5C1), CircleShape).border(4.dp, Color.Black, CircleShape).clickable {
                    scope.launch {
                        if (existingSessionId != null) {
                            dao.deleteSessionById(existingSessionId)
                            dao.deleteConsumptionsForSession(existingSessionId)
                        }
                        val sessionId = dao.insertSession(Session(pubId = pubId, dateMillis = System.currentTimeMillis(), totalSpent = grandTotal))
                        val dbConsumptions = consumptions.map { (pId, drink) ->
                            Consumption(sessionId = sessionId.toInt(), personId = pId, drinkName = drink.name, drinkIcon = drink.icon, price = drink.price)
                        }
                        dao.insertConsumptions(dbConsumptions)
                        onEndSession()
                    }
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "Konec", Modifier.size(50.dp))
                }
            }
        }

        if (showEditChoiceDialog) {
            Dialog(onDismissRequest = { showEditChoiceDialog = false }) {
                Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Co chceš upravit?", fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(bottom = 24.dp))
                        HeavyButton("👤 Lidi u stolu", modifier = Modifier.fillMaxWidth(), onClick = { showEditChoiceDialog = false; showEditParticipants = true }, enabled = true)
                        Spacer(Modifier.height(16.dp))
                        HeavyButton("🍺 Nápojový lístek", modifier = Modifier.fillMaxWidth(), onClick = { showEditChoiceDialog = false; showDrinkEditor = true }, enabled = true)
                        Spacer(Modifier.height(16.dp))
                        HeavyButton("Zrušit", modifier = Modifier.fillMaxWidth(), onClick = { showEditChoiceDialog = false }, enabled = true)
                    }
                }
            }
        }

        if (addDrinkForPerson != null) {
            Dialog(onDismissRequest = { addDrinkForPerson = null }) {
                Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
                    Column {
                        Text("Co si dává?", fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(drinksMenu) { drink ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { consumptions.add(Pair(addDrinkForPerson!!, drink)); addDrinkForPerson = null }.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${drink.icon} ${drink.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("${drink.price} Kč", fontSize = 20.sp, fontWeight = FontWeight.Black)
                                }
                                Divider(color = Color.Black)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        HeavyButton("Zrušit", modifier = Modifier.fillMaxWidth(), onClick = { addDrinkForPerson = null }, enabled = true)
                    }
                }
            }
        }

        if (showEditParticipants) {
            SelectFriendsDialog(allFriends = allFriends, selectedIds = activeParticipantIds, onSelectionChange = { activeParticipantIds = it }, onAddNewFriend = { showEditParticipants = false; showAddNewFriend = true }, onDismiss = { showEditParticipants = false })
        }

        if (showAddNewFriend) {
            AddEditFriendDialog(person = null, onDismiss = { showAddNewFriend = false; showEditParticipants = true }, onSave = { name -> scope.launch { dao.insertPerson(Person(name = name)); showAddNewFriend = false; showEditParticipants = true } }, onDelete = {} )
        }

        if (showDrinkEditor) {
            EditDrinkMenuDialog(
                currentDrinks = drinksMenu, onDismiss = { showDrinkEditor = false },
                onAddDrink = { name, price, icon -> scope.launch { dao.insertDrinks(listOf(Drink(pubId = pubId, name = name, price = price.toIntOrNull() ?: 0, icon = icon))) } },
                onUpdateDrink = { drink, newName, newPrice, newIcon -> scope.launch { dao.updateDrink(drink.copy(name = newName, price = newPrice.toIntOrNull() ?: 0, icon = newIcon)) } },
                onDeleteDrink = { drink -> scope.launch { dao.deleteDrink(drink) } }
            )
        }
    }
}

// =====================================================================================
// --- HISTORIE (KLIKACÍ KARTY) ---
// =====================================================================================
@Composable
fun HistoryScreen(userName: String, dao: PubDao, onSessionClick: (Int) -> Unit) {
    // OPRAVA: initial = null (čekáme na data)
    val sessionsState by dao.getAllSessions().collectAsState(initial = null)
    val pubs by dao.getAllPubs().collectAsState(emptyList())

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        LazyColumn(Modifier.fillMaxSize().padding(24.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Text("Historie akcí", fontSize = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
            }

            val sessions = sessionsState
            if (sessions == null) {
                // Fáze načítání: Neukazujeme nic (nebo se tu dá dát načítací kolečko)
            } else if (sessions.isEmpty()) {
                // Databáze odpověděla a potvrdila, že je opravdu prázdná
                item { Text("Zatím jsi nikde nebyl. Běž do hospody! 🍻", fontSize = 18.sp) }
            } else {
                // Máme historii, můžeme ji vypsat
                items(sessions) { session ->
                    val pub = pubs.find { it.id == session.pubId }
                    val dateStr = SimpleDateFormat("d. M. yyyy (HH:mm)", Locale.getDefault()).format(Date(session.dateMillis))
                    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).clickable { onSessionClick(session.id) }.padding(16.dp)) {
                        Column {
                            Text(pub?.name ?: "Neznámá hospoda", fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text(dateStr, fontSize = 16.sp, color = Color.DarkGray)
                            Spacer(Modifier.height(8.dp))
                            Text("Celková útrata: ${session.totalSpent} Kč", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun PubDetailScreen(
    pubId: Int,
    dao: PubDao,
    sessionParticipants: Set<Int>,
    onUpdateParticipants: (Set<Int>) -> Unit,
    onBack: () -> Unit,
    onStartDrinking: () -> Unit
) {
    val allPubs by dao.getAllPubs().collectAsState(emptyList())
    val pub = allPubs.find { it.id == pubId }
    val drinks by dao.getDrinksForPub(pubId).collectAsState(emptyList())
    val allFriends by dao.getAllPersons().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var showFriendSelector by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showDrinkEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // --- HORNÍ LIŠTA ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Zpět",
                    modifier = Modifier.size(40.dp).clickable { onBack() },
                    tint = Color.Black
                )

                HeavyButton(
                    text = "👤 Přidat kámoše",
                    // OPRAVA: Modifier patří k modifier, onClick k onClick!
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(48.dp),
                    onClick = { showFriendSelector = true },
                    enabled = true
                )

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .background(Color(0xFFE5D5C1), RoundedCornerShape(16.dp))
                        .border(3.dp, Color.Black, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤 ${sessionParticipants.size + 1}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            pub?.let {
                if (it.imageUri != null) {
                    AsyncImage(it.imageUri, null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(Color.LightGray, RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)))
                }
                Spacer(Modifier.height(16.dp))
                Text(it.name.uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text(it.description, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.weight(1f)) // Tohle teď bude fungovat správně

            // --- BAREVNÁ TLAČÍTKA AKCÍ ---

            // 1. ZAČÍT CHLASTAT (Zelené)
            Button(
                onClick = onStartDrinking,
                modifier = Modifier.fillMaxWidth().height(60.dp).border(3.dp, Color.Black, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ZAČÍT CHLASTAT 🍻", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }

            Spacer(Modifier.height(12.dp))

            // 2. UPRAVIT LÍSTEK (Oranžové)
            Button(
                onClick = { showDrinkEditor = true },
                modifier = Modifier.fillMaxWidth().height(56.dp).border(3.dp, Color.Black, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Upravit nápojový lístek 📝", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            // 3. SMAZAT HOSPODU (Červené)
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(56.dp).border(3.dp, Color.Black, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Smazat hospodu 🗑️", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(Modifier.height(32.dp))
        }

        // --- POTVRZENÍ SMAZÁNÍ ---
        if (showDeleteConfirm) {
            Dialog(onDismissRequest = { showDeleteConfirm = false }) {
                Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Opravdu smazat?", fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Smažeš hospodu i s jejím lístkem.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // OPRAVA: Tady jsi měl Modifier a onClick prohozené!
                            HeavyButton(
                                text = "Zrušit",
                                modifier = Modifier.weight(1f),
                                onClick = { showDeleteConfirm = false }
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        pub?.let {
                                            dao.deleteDrinksForPub(it.id)
                                            dao.deletePub(it)
                                        }
                                        onBack()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp).border(3.dp, Color.Black, RoundedCornerShape(12.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Smazat", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- OSTATNÍ DIALOGY ---
        if (showFriendSelector) SelectFriendsDialog(allFriends, sessionParticipants, onUpdateParticipants, { showFriendSelector = false; showAddFriendDialog = true }) { showFriendSelector = false }
        if (showAddFriendDialog) AddEditFriendDialog(null, { showAddFriendDialog = false; showFriendSelector = true }, { name -> scope.launch { dao.insertPerson(Person(name = name)); showAddFriendDialog = false; showFriendSelector = true } }) {}
        if (showDrinkEditor) EditDrinkMenuDialog(drinks, { showDrinkEditor = false }, { name, price, icon -> scope.launch { dao.insertDrinks(listOf(Drink(pubId = pubId, name = name, price = price.toIntOrNull() ?: 0, icon = icon))) } }, { drink, newName, newPrice, newIcon -> scope.launch { dao.updateDrink(drink.copy(name = newName, price = newPrice.toIntOrNull() ?: 0, icon = newIcon)) } }, { drink -> scope.launch { dao.deleteDrink(drink) } })
    }
}


@Composable
fun SelectFriendsDialog(allFriends: List<Person>, selectedIds: Set<Int>, onSelectionChange: (Set<Int>) -> Unit, onAddNewFriend: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
            Column {
                Text("Kdo jde pít?", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.height(16.dp))
                HeavyButton("➕ Přidat nového", onClick = onAddNewFriend, enabled = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 250.dp).padding(vertical = 12.dp)) {
                    items(allFriends) { friend ->
                        val isSelected = selectedIds.contains(friend.id)
                        Row(Modifier.fillMaxWidth().clickable { onSelectionChange(if (isSelected) selectedIds - friend.id else selectedIds + friend.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).border(3.dp, Color.Black, RoundedCornerShape(6.dp)).background(if (isSelected) Color.Black else Color.Transparent))
                            Spacer(Modifier.width(16.dp))
                            Text(friend.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HeavyButton("Hotovo", onClick = onDismiss, enabled = true)
            }
        }
    }
}

@Composable
fun EditDrinkMenuDialog(currentDrinks: List<Drink>, onDismiss: () -> Unit, onAddDrink: (String, String, String) -> Unit, onUpdateDrink: (Drink, String, String, String) -> Unit, onDeleteDrink: (Drink) -> Unit) {
    var n by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🍺") }
    var editingDrink by remember { mutableStateOf<Drink?>(null) }
    val availableIcons = listOf("🍺", "🍷", "🥃", "🍹", "☕", "🥤")

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
            Column {
                Text(if (editingDrink == null) "Nápojový lístek" else "Upravit položku", fontWeight = FontWeight.Black, fontSize = 24.sp)
                LazyColumn(Modifier.heightIn(max = 200.dp).padding(vertical = 12.dp)) {
                    items(currentDrinks) { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.icon} ${d.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${d.price} Kč", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Default.Edit, "Upravit", Modifier.size(20.dp).clickable { editingDrink = d; n = d.name; p = d.price.toString(); selectedIcon = d.icon }, tint = Color.Black)
                            }
                        }
                    }
                }
                Divider(color = Color.Black, thickness = 2.dp)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    availableIcons.forEach { icon ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedIcon == icon) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedIcon = icon }
                        ) {
                            Text(icon, fontSize = 24.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HeavyTextField(n, { n = it }, "Název drinku")
                Spacer(Modifier.height(8.dp))
                HeavyTextField(p, { p = it }, "Cena (Kč)")
                Spacer(Modifier.height(16.dp))
                if (editingDrink == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeavyButton("Přidat", modifier = Modifier.weight(1f), onClick = { onAddDrink(n, p, selectedIcon); n = ""; p = ""; selectedIcon = "🍺" }, enabled = n.isNotBlank() && p.isNotBlank())
                        HeavyButton("Hotovo", modifier = Modifier.weight(1f), onClick = onDismiss, enabled = true)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeavyButton("Uložit", modifier = Modifier.weight(1f), onClick = { onUpdateDrink(editingDrink!!, n, p, selectedIcon); editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = n.isNotBlank() && p.isNotBlank())
                        HeavyButton("Smazat", modifier = Modifier.weight(1f), onClick = { onDeleteDrink(editingDrink!!); editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    HeavyButton("Zrušit úpravu", modifier = Modifier.fillMaxWidth(), onClick = { editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = true)
                }
            }
        }
    }
}

@Composable
fun CreatePubScreen(onBack: () -> Unit, onSave: (String, String, Uri?, List<Drink>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val tempDrinks = remember { mutableStateListOf<Drink>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri = it }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Nová hospoda", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            HeavyTextField(name, { name = it }, "Název")
            Spacer(Modifier.height(16.dp))
            HeavyTextField(desc, { desc = it }, "Popis")
            Spacer(Modifier.height(16.dp))
            HeavyButton(if (uri == null) "Přidat fotku 📷" else "Fotka vybrána ✅", onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = true)
            Spacer(Modifier.height(16.dp))
            HeavyButton("Lístek (${tempDrinks.size}) 🍺", onClick = { showDialog = true }, enabled = true)
            Spacer(Modifier.weight(1f))
            HeavyButton("Uložit", onClick = { onSave(name, desc, uri, tempDrinks.toList()) }, enabled = name.isNotBlank())
        }
        if (showDialog) {
            AddDrinkDialog(tempDrinks, { showDialog = false }, { n, p, icon -> tempDrinks.add(Drink(0, 0, n, p.toIntOrNull() ?: 0, icon)) }, { oldDrink, n, p, icon -> val index = tempDrinks.indexOf(oldDrink); if (index != -1) tempDrinks[index] = oldDrink.copy(name = n, price = p.toIntOrNull() ?: 0, icon = icon) }, { drink -> tempDrinks.remove(drink) })
        }
    }
}

@Composable
fun AddDrinkDialog(currentDrinks: List<Drink>, onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit, onUpdate: (Drink, String, String, String) -> Unit, onDelete: (Drink) -> Unit) {
    var n by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🍺") }
    var editingDrink by remember { mutableStateOf<Drink?>(null) }
    val availableIcons = listOf("🍺", "🍷", "🥃", "🍹", "☕", "🥤")

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
            Column {
                Text(if (editingDrink == null) "Přidat položku" else "Upravit položku", fontWeight = FontWeight.Black, fontSize = 24.sp)
                LazyColumn(Modifier.heightIn(max = 150.dp).padding(vertical = 12.dp)) {
                    items(currentDrinks) { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.icon} ${d.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${d.price} Kč", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Default.Edit, "Upravit", Modifier.size(20.dp).clickable { editingDrink = d; n = d.name; p = d.price.toString(); selectedIcon = d.icon }, tint = Color.Black)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.Black))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    availableIcons.forEach { icon ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedIcon == icon) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedIcon = icon }
                        ) {
                            Text(icon, fontSize = 24.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HeavyTextField(n, { n = it }, "Název")
                Spacer(Modifier.height(8.dp))
                HeavyTextField(p, { p = it }, "Cena")
                Spacer(Modifier.height(16.dp))
                if (editingDrink == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeavyButton("Přidat", modifier = Modifier.weight(1f), onClick = { onAdd(n, p, selectedIcon); n = ""; p = ""; selectedIcon = "🍺" }, enabled = n.isNotBlank() && p.isNotBlank())
                        HeavyButton("Hotovo", modifier = Modifier.weight(1f), onClick = onDismiss, enabled = true)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeavyButton("Uložit", modifier = Modifier.weight(1f), onClick = { onUpdate(editingDrink!!, n, p, selectedIcon); editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = n.isNotBlank() && p.isNotBlank())
                        HeavyButton("Smazat", modifier = Modifier.weight(1f), onClick = { onDelete(editingDrink!!); editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    HeavyButton("Zrušit úpravu", modifier = Modifier.fillMaxWidth(), onClick = { editingDrink = null; n = ""; p = ""; selectedIcon = "🍺" }, enabled = true)
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onNavigateToHome: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.pozadi_uivod),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 100.dp) // Tady jsme přidali víc místa od horního okraje
        ) {
            Text(
                text = "Ahoj, jak se\njmenuješ?",
                fontSize = 42.sp, // Trochu zmenšeno, aby se text "nelámal" o okraj
                fontWeight = FontWeight.Black,
                lineHeight = 48.sp,
                color = Color.Black
            )

            // TENTO SPACER JE KLÍČOVÝ - vytlačí políčko níž pod text
            Spacer(Modifier.height(64.dp))

            HeavyTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = "Zadej jméno"
            )

            Spacer(Modifier.weight(1f))

            HeavyButton(
                text = "Pokračovat",
                onClick = { onNavigateToHome(nameInput) },
                enabled = nameInput.isNotBlank()
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun HomeScreen(userName: String, pubDao: PubDao, onCreatePubClick: () -> Unit, onPubClick: (Int) -> Unit) {
    val pubs by pubDao.getAllPubs().collectAsState(initial = emptyList())
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 32.dp, bottom = 16.dp)) {
            item {
                Text("Ahoj, $userName!", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                BigAddCard("Vytvořit novou\nhospodu", onCreatePubClick)
                Spacer(Modifier.height(24.dp))
            }
            items(pubs) { pub ->
                Box(Modifier.fillMaxWidth().background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).clickable { onPubClick(pub.id) }.padding(16.dp)) {
                    Column {
                        if (pub.imageUri != null) {
                            AsyncImage(pub.imageUri, null, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(pub.name, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(pub.description, fontSize = 16.sp, color = Color.DarkGray)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FriendsScreen(userName: String, dao: PubDao, onFriendClick: (Int) -> Unit) {
    val friends by dao.getAllPersons().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var friendToEdit by remember { mutableStateOf<Person?>(null) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 32.dp, bottom = 16.dp)) {
            item {
                Text("Lidé", fontSize = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                HeavyButton(text = "Přidat kámoše 🍻", onClick = { friendToEdit = null; showDialog = true }, enabled = true)
                Spacer(Modifier.height(32.dp))
            }
            items(friends) { friend ->
                Box(Modifier.fillMaxWidth().background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).clickable { onFriendClick(friend.id) }.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.name, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Icon(Icons.Default.Edit, "Upravit", Modifier.size(28.dp).clickable { friendToEdit = friend; showDialog = true }, tint = Color.Black)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (showDialog) AddEditFriendDialog(friendToEdit, { showDialog = false }, { name -> scope.launch { if (friendToEdit == null) dao.insertPerson(Person(name = name)) else dao.updatePerson(friendToEdit!!.copy(name = name)); showDialog = false } }, { scope.launch { friendToEdit?.let { dao.deletePerson(it) }; showDialog = false } })
    }
}

@Composable
fun FriendDetailScreen(personId: Int, dao: PubDao, onBack: () -> Unit) {
    val person by dao.getPersonById(personId).collectAsState(initial = null)
    // Načteme všechny drinky, co tento člověk vypil
    val consumptions by dao.getAllConsumptionsForPerson(personId).collectAsState(initial = emptyList())

    // Výpočty statistik
    val totalSpent = consumptions.sumOf { it.price }
    // Seskupíme drinky podle jména a ikony, abychom spočítali kusy
    val drinkStats = consumptions.groupBy { it.drinkIcon to it.drinkName }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second } // Nejpoužívanější nahoře

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("← Zpět", Modifier.clickable { onBack() }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))

            person?.let {
                Text(it.name, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                // Černá dělící čára, aby to ladilo s detaily sezení
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.Black))
                Spacer(Modifier.height(24.dp))

                // CELKOVÉ STATISTIKY V BUBLINĚ
                Box(Modifier.fillMaxWidth().background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(20.dp)) {
                    Column {
                        Text("Celková útrata", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("$totalSpent Kč", fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(12.dp))
                        Text("Celkem vypito drinků", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${consumptions.size} ks", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("Co nejvíc pije:", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))

                // SEZNAM KONKRÉTNÍCH DRINKŮ
                LazyColumn(Modifier.weight(1f)) {
                    if (drinkStats.isEmpty()) {
                        item { Text("Zatím nemáš žádný záznam. Běžte na pivo! 🍻", color = Color.Gray) }
                    }
                    items(drinkStats) { (drinkInfo, count) ->
                        val (icon, name) = drinkInfo
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(icon, fontSize = 32.sp)
                                Spacer(Modifier.width(16.dp))
                                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            // Číslo v kroužku
                            Box(
                                Modifier.size(40.dp).background(Color.Black, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${count}x", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                        Divider(color = Color.Black.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditFriendDialog(person: Person?, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(person?.name ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.background(Color(0xFFE5D5C1), RoundedCornerShape(24.dp)).border(3.dp, Color.Black, RoundedCornerShape(24.dp)).padding(24.dp)) {
            Column {
                Text(if (person == null) "Nový kámoš" else "Upravit kámoše", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.height(16.dp))
                HeavyTextField(name, { name = it }, "Jméno")
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeavyButton("Uložit", modifier = Modifier.weight(1f), onClick = { onSave(name) }, enabled = name.isNotBlank())
                    HeavyButton("Zrušit", modifier = Modifier.weight(1f), onClick = onDismiss, enabled = true)
                }
                if (person != null) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.fillMaxWidth()) { Text("Smazat kámoše", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(currentScreen: String, onNavigate: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(70.dp).background(Color(0xFFE5D5C1)).border(width = 2.dp, color = Color.Black)) {
        Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            NavIcon(Icons.Default.Home, isActive = currentScreen == "home" || currentScreen == "pub_detail", onClick = { onNavigate("home") })
            NavIcon(Icons.Default.Search, isActive = currentScreen == "history" || currentScreen == "session_detail", onClick = { onNavigate("history") })
            NavIcon(Icons.Default.Menu, isActive = currentScreen == "friends_list" || currentScreen == "friend_detail", onClick = { onNavigate("friends_list") })

            // OPRAVENO: Přidána navigace a aktivní stav
            NavIcon(
                icon = Icons.Default.Person,
                isActive = currentScreen == "profile",
                onClick = { onNavigate("profile") } // Teď už to ví, že má jít na profil!
            )
        }
    }
}

@Composable
fun NavIcon(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Icon(icon, null, Modifier.size(36.dp).clickable { onClick() }, tint = if (isActive) Color.Black else Color.Gray)
}

fun sklonujJmeno(jmeno: String): String {
    if (jmeno.isBlank()) return jmeno
    val j = jmeno.trim()
    val koncovka = j.lowercase().last()

    return when {
        // Nela -> Nelo, Petra -> Petro
        j.lowercase().endsWith("a") -> j.dropLast(1) + "o"

        // Šimon -> Šimone, Martin -> Martine
        j.lowercase().endsWith("n") -> j + "e"

        // Max -> Maxi, Filip -> Filipe (tady je to složitější, ale 'i' neurazí)
        j.lowercase().endsWith("x") || j.lowercase().endsWith("s") || j.lowercase().endsWith("š") -> j + "i"

        // Lukáš -> Lukáši, Matěj -> Matěji
        j.lowercase().endsWith("j") || j.lowercase().endsWith("ř") -> j + "i"

        // Výchozí pro ostatní (většinou funguje přidání 'e' nebo 'i')
        else -> if ("aeiouy".contains(koncovka)) j else j + "e"
    }
}

@Composable
fun ProfileScreen(currentName: String, onSave: (String) -> Unit) {
    var nameInput by remember { mutableStateOf(currentName) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.pozadi_uivod), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Můj Profil", fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(32.dp))

            Text("Změnit jméno:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Políčko pro zadání jména
            HeavyTextField(nameInput, { nameInput = it }, "Tvé jméno")

            Spacer(Modifier.height(16.dp))

            // Tady se ukazuje to automatické skloňování
            Text(
                text = "Aplikace tě bude oslovovat: ${sklonujJmeno(nameInput)}",
                fontSize = 16.sp,
                color = Color.DarkGray
            )

            Spacer(Modifier.weight(1f))

            HeavyButton(
                text = "Uložit změny",
                onClick = { onSave(nameInput) },
                enabled = nameInput.isNotBlank() && nameInput != currentName
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

