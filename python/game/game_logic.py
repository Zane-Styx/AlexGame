"""
Game logic for the Gesture Memory Game.
Handles sequence generation, validation, and difficulty progression.
"""
from typing import List, Optional
import random
import time


class GestureMemoryGame:
    """
    Gesture Memory Game logic.
    
    Player must repeat a random gesture sequence of 4 gestures.
    Each round has a 15-second timer. Player can retry until time runs out.
    """
    
    # Fixed sequence length
    SEQUENCE_LENGTH = 4
    
    # Time limit per round (seconds)
    TIME_LIMIT = 15.0
    
    # All available gestures
    ALL_GESTURES = [1, 2, 4, 5, 6, 7, 10]  # Peace, OK, HighFive, Fist, Point, Rock, ILoveYou
    
    def __init__(self, difficulty: str = "medium"):
        """
        Initialize game.
        
        Args:
            difficulty: Not used anymore, kept for compatibility
        """
        self.difficulty = difficulty
        
        self.sequence: List[int] = []  # Target sequence (always 4 gestures)
        self.player_input: List[int] = []  # Player's input so far
        self.current_round = 0
        self.score = 0
        self.game_over = False
        self.won = False
        self.round_start_time = 0.0  # When current round started
        self.time_remaining = self.TIME_LIMIT
        self.last_timeout_round = -1  # Track which round had timeout to prevent duplicates
        
        # Start first round
        self._advance_round()
    
    def _advance_round(self):
        """Start a new round with a new 4-gesture sequence."""
        self.current_round += 1
        
        # Generate new random sequence of exactly 4 gestures
        self.sequence = [random.choice(self.ALL_GESTURES) for _ in range(self.SEQUENCE_LENGTH)]
        
        self.player_input = []
        self.round_start_time = time.time()
        self.time_remaining = self.TIME_LIMIT
    
    def get_time_remaining(self) -> float:
        """Get the time remaining in the current round."""
        if self.game_over:
            return 0.0
        elapsed = time.time() - self.round_start_time
        self.time_remaining = max(0.0, self.TIME_LIMIT - elapsed)
        return self.time_remaining
    
    def is_time_expired(self) -> bool:
        """Check if the current round's timer has expired."""
        return self.get_time_remaining() <= 0.0
    
    def check_and_handle_timeout(self) -> dict:
        """
        Check if timer expired and handle it (advance round with penalty).
        Returns dict with timeout info if timeout occurred and wasn't handled yet.
        Only triggers once per round.
        """
        if self.is_time_expired() and len(self.player_input) < len(self.sequence) and self.current_round != self.last_timeout_round:
            # Timer expired and sequence not complete - FAIL and advance
            self.last_timeout_round = self.current_round
            self._advance_round()
            return {
                'timeout_occurred': True,
                'new_sequence': self.sequence.copy(),
            }
        return {'timeout_occurred': False}
    
    def input_gesture(self, gesture_id: int) -> dict:
        """
        Process player gesture input.
        
        Args:
            gesture_id: Gesture ID from ALL_GESTURES or None for no gesture
        
        Returns:
            {
                'valid': bool,  # Correct gesture (matches expected)
                'complete': bool,  # Sequence complete
                'correct_so_far': bool,  # Input matches sequence so far
                'progress': (current, total),  # (3, 4) means 3 of 4
                'expected': int,  # Next expected gesture
                'mistake': bool,  # Made a mistake
                'time_expired': bool,  # Timer ran out
                'time_remaining': float,  # Seconds remaining
            }
        """
        # Update time remaining
        time_remaining = self.get_time_remaining()
        
        # Don't auto-advance when timer expires - let player keep trying
        # Timer hitting 0 just means no time bonus, but sequence stays the same
        
        # Already completed this round
        if len(self.player_input) >= len(self.sequence):
            return {
                'valid': False,
                'complete': True,
                'correct_so_far': True,
                'progress': (len(self.player_input), len(self.sequence)),
                'expected': None,
                'mistake': False,
                'time_expired': False,
                'time_remaining': time_remaining,
            }

        # Invalid gesture
        if gesture_id is None or gesture_id not in self.ALL_GESTURES:
            expected = None
            if len(self.player_input) < len(self.sequence):
                expected = self.sequence[len(self.player_input)]
            return {
                'valid': False,
                'complete': False,
                'correct_so_far': True,
                'progress': (len(self.player_input), len(self.sequence)),
                'expected': expected,
                'mistake': False,  # Not a mistake, just no valid gesture yet
                'time_expired': False,
                'time_remaining': time_remaining,
            }
        
        # Check if it matches the expected gesture
        expected = self.sequence[len(self.player_input)]
        is_correct = gesture_id == expected
        
        if is_correct:
            self.player_input.append(gesture_id)
            self.score += 1
            
            # Check if sequence is complete
            if len(self.player_input) == len(self.sequence):
                # Sequence completed successfully - advance to next round
                self._advance_round()
                
                return {
                    'valid': True,
                    'complete': True,
                    'correct_so_far': True,
                    'progress': (self.SEQUENCE_LENGTH, self.SEQUENCE_LENGTH),
                    'expected': None,
                    'mistake': False,
                    'time_expired': False,
                    'time_remaining': self.TIME_LIMIT,
                    'new_sequence': self.sequence.copy(),
                }
            else:
                # Correct but not complete yet
                return {
                    'valid': True,
                    'complete': False,
                    'correct_so_far': True,
                    'progress': (len(self.player_input), len(self.sequence)),
                    'expected': self.sequence[len(self.player_input)] if len(self.player_input) < len(self.sequence) else None,
                    'mistake': False,
                    'time_remaining': time_remaining,
                }
        else:
            # Mistake - ignore wrong gesture and keep current progress.
            return {
                'valid': False,
                'complete': False,
                'correct_so_far': True,
                'progress': (len(self.player_input), len(self.sequence)),
                'expected': self.sequence[len(self.player_input)],
                'mistake': False,
                'time_remaining': time_remaining,
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
            'time_remaining': self.get_time_remaining(),
        }
