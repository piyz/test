package by.matrosov.demodemo.controller;

import by.matrosov.demodemo.model.ChatMessage;
import by.matrosov.demodemo.model.DrawMessage;
import by.matrosov.demodemo.service.game.GameService;
import by.matrosov.demodemo.service.rooms.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Random;

import static java.lang.String.format;

@Controller
public class WebSocketController {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private GameService gameService;

    @Autowired
    private RoomService roomService;

    @MessageMapping("/chat/{roomId}/timer")
    public void updateTimer(@DestinationVariable String roomId) {
        messagingTemplate.convertAndSend(format("/topic/%s/timer", roomId), new ChatMessage());
    }

    @MessageMapping("/chat/table")
    public void updateTable(@Payload ChatMessage chatMessage) {
        //close room
        roomService.changeRoomState(Integer.parseInt(chatMessage.getContent()));
        messagingTemplate.convertAndSend("/topic/table", chatMessage);
    }

    @MessageMapping("/chat/{roomId}/sendMessage")
    public void sendMessage(@DestinationVariable String roomId, @Payload ChatMessage chatMessage) {
        messagingTemplate.convertAndSend(format("/topic/%s/public", roomId), chatMessage);
    }

    @MessageMapping("/chat/{roomId}/changeDrawUser")
    public void changeDrawUser(@DestinationVariable String roomId, @Payload ChatMessage chatMessage, Principal principal){

        //get prev user
        String prevUser = chatMessage.getContent();

        //next user
        String name = "";

        //send message to the chat
        messagingTemplate.convertAndSend(format("/topic/%s/public", roomId), chatMessage);

        if (prevUser != null){
            //add score
            if (gameService.addScore(prevUser, principal.getName(), roomId)){
                //is end
                chatMessage.setContent(gameService.getScore(roomId));
                messagingTemplate.convertAndSend(format("/topic/%s/end", roomId), chatMessage);

                //open room
                roomService.changeRoomState(Integer.parseInt(roomId));
                messagingTemplate.convertAndSend("/topic/table", chatMessage);
            }else {
                //set prev user to disable canvas
                messagingTemplate.convertAndSendToUser(prevUser, "/queue/canvas", chatMessage);
                name = gameService.getNextUser(prevUser, roomId);
            }
            gameService.print();
        }else {
            name = principal.getName();
        }

        //send modal window
        chatMessage.setContent(gameService.getRandomWords());
        messagingTemplate.convertAndSendToUser(name, "/queue/sendModal", chatMessage);

        // set current user to DRAWING
        chatMessage.setSender(name);
        messagingTemplate.convertAndSend(format("/topic/%s/changeDrawUser", roomId), chatMessage);

        //set current user to enable canvas
        messagingTemplate.convertAndSendToUser(name, "/queue/canvas", chatMessage);
    }

    @MessageMapping("/chat/{roomId}/addUser")
    public void addUser(@DestinationVariable String roomId, @Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String currentRoomId = (String) headerAccessor.getSessionAttributes().put("room_id", roomId);
        if (currentRoomId != null) {
            ChatMessage leaveMessage = new ChatMessage();
            leaveMessage.setType(ChatMessage.MessageType.LEAVE);
            leaveMessage.setSender(chatMessage.getSender());
            messagingTemplate.convertAndSend(format("/topic/%s/public", currentRoomId), leaveMessage);
        }
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        messagingTemplate.convertAndSend(format("/topic/%s/public", roomId), chatMessage);
    }

    @MessageMapping("/chat/{roomId}/changeGuess")
    public void changeGuess(@DestinationVariable String roomId, @Payload ChatMessage chatMessage){

        String word = chatMessage.getContent();

        Random random = new Random();
        String s = "";
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < 3; i++) {
            int r = random.nextInt(word.length());
            while (s.contains(String.valueOf(r))){
                r = random.nextInt(word.length());
            }
            sb.append(r);
        }

        String resultContent = word + "#" + sb;
        chatMessage.setContent(resultContent);

        messagingTemplate.convertAndSend(format("/topic/%s/changeGuess", roomId), chatMessage);
    }

    @MessageMapping("/chat/{roomId}/draw")
    public void draw(@DestinationVariable String roomId, @Payload ChatMessage chatMessage){
        DrawMessage drawMessage = new DrawMessage();
        drawMessage.setSender(chatMessage.getSender());
        drawMessage.setX1(Float.parseFloat(chatMessage.getContent().split("#")[0].split(",")[0]));
        drawMessage.setY1(Float.parseFloat(chatMessage.getContent().split("#")[0].split(",")[1]));
        drawMessage.setX2(Float.parseFloat(chatMessage.getContent().split("#")[1].split(",")[0]));
        drawMessage.setY2(Float.parseFloat(chatMessage.getContent().split("#")[1].split(",")[1]));
        drawMessage.setColor("#" + chatMessage.getContent().split("#")[2]);

        messagingTemplate.convertAndSend(format("/topic/%s/draw", roomId), drawMessage);
    }

    @MessageMapping("/chat/{roomId}/score")
    public void getScore(@DestinationVariable String roomId){
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(gameService.getScore(roomId));
        messagingTemplate.convertAndSend(format("/topic/%s/score", roomId), chatMessage);
    }

    @MessageMapping("/chat/{roomId}/guessDisplay")
    public void clearGuessDisplay(@DestinationVariable String roomId){
        ChatMessage chatMessage = new ChatMessage();
        messagingTemplate.convertAndSend(format("/topic/%s/guessDisplay", roomId), chatMessage);
    }
}
