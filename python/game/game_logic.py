"""
Game logic for the Gesture Memory Game.
Handles sequence generation, validation, and difficulty progression.
"""
from typing import List, Optional
import random


class GestureMemoryGame:
    """
    Gesture Memory Game logic.
    
    Player must repeat a random gesture sequence.
    Sequence grows longer each round.
    """
    
    # Difficulty levels: (initial_length, max_length)
    DIFFICULTIES = {
        "easy": (3, 6),
        "medium": (4, 8),
        "hard": (5, 12),
    }
    
    def __init__(self, difficulty: str = "medium"):
        """
        Initialize game.
        
        Args:
            difficulty: One of "easy", "medium", "hard"
        """
        if difficulty not in self.DIFFICULTIES:
            difficulty = "medium"
        
        self.difficulty = difficulty
        self.initial_length, self.max_length = self.DIFFICULTIES[difficulty]
        
        self.sequence: List[int] = []  # Target sequence
        self.player_input: List[int] = []  # Player's input so far
        self.current_round = 0
        self.score = 0
        self.game_over = False
        self.won = False
        
        # Start first round
        self._advance_round()
    
    def _advance_round(self):
        """Start a new round by extending the sequence."""
        self.current_round += 1
        
        # Determine sequence length for this round
        current_length = min(
            self.initial_length + (self.current_round - 1),
            self.max_length
        )
        
        # Generate or extend sequence
        if self.current_round == 1:
            self.sequence = [random.randint(1, 4) for _ in range(current_length)]
        else:
            # Add one more gesture to existing sequence
            self.sequence.append(random.randint(1, 4))
        
        self.player_input = []
        self.score = len(self.sequence) - self.initial_length
    
    def input_gesture(self, gesture_id: int) -> dict:
        """
        Process player gesture input.
        
        Args:
            gesture_id: Gesture ID (1-4) or None for no gesture
        
        Returns:
            {
                'valid': bool,  # Correct gesture
                'complete': bool,  # Sequence complete
                'correct_so_far': bool,  # Input matches sequence so far
                'progress': (current, total),  # (3, 6) means 3 of 6
                'expected': int,  # Next expected gesture
                'mistake': bool,  # Made a mistake
            }
        """
        if gesture_id is None or not (1 <= gesture_id <= 4):
            return {
                'valid': False,
                'complete': False,
                'correct_so_far': True,
                'progress': (len(self.player_input), len(self.sequence)),
                'expected': self.sequence[len(self.player_input)],
                'mistake': False,
            }
        
        # Check if it matches the expected gesture
        expected = self.sequence[len(self.player_input)]
        is_correct = gesture_id == expected
        
        if is_correct:
            self.player_input.append(gesture_id)
            
            # Check if sequence is complete
            if len(self.player_input) == len(self.sequence):
                # Check if we've reached max length
                if len(self.sequence) == self.max_length:
                    self.game_over = True
                    self.won = True
                else:
                    # Advance to next round
                    self._advance_round()
                
                return {
                    'valid': True,
                    'complete': True,
                    'correct_so_far': True,
                    'progress': (len(self.player_input), len(self.sequence)),
                    'expected': None,
                    'mistake': False,
                }
        else:
            # Mistake - do NOT end game, just report mistake
            return {
                'valid': False,
                'complete': False,
                'correct_so_far': False,
                'progress': (len(self.player_input), len(self.sequence)),
                'expected': expected,
                'mistake': True,
            }
        
        return {
            'valid': True,
            'complete': False,
            'correct_so_far': True,
            'progress': (len(self.player_input), len(self.sequence)),
            'expected': self.sequence[len(self.player_input)] if len(self.player_input) < len(self.sequence) else None,
            'mistake': False,
        }
    
    def get_sequence_display(self) -> List[int]:
        """Get the full sequence to display at round start."""
        return self.sequence.copy()
    
    def get_next_expected(self) -> Optional[int]:
        """Get the next gesture the player needs to input."""
        if len(self.player_input) < len(self.sequence):
            return self.sequence[len(self.player_input)]
        return None
    
    def get_progress(self) -> tuple[int, int]:
        """Get (current_index, total_length) of player progress."""
        return (len(self.player_input), len(self.sequence))
    
    def get_stats(self) -> dict:
        """Get game statistics."""
        return {
            'round': self.current_round,
            'sequence_length': len(self.sequence),
            'score': self.score,
            'difficulty': self.difficulty,
            'game_over': self.game_over,
            'won': self.won,
        }
